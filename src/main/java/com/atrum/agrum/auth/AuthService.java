package com.atrum.agrum.auth;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.exception.InvalidCredentialsException;
import com.atrum.agrum.exception.ResourceNotFoundException;
import com.atrum.agrum.security.JwtTokenProvider;
import com.atrum.agrum.security.PermissionCacheEvictor;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.atrum.agrum.auth.AuthDto.*;
import com.atrum.agrum.estate.Estate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {


    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final PermissionChecker permissionChecker;
    private final PermissionCacheEvictor permissionCacheEvictor;

    private static final String REDIS_PREFIX = "refresh_session:";
    private static final Duration SESSION_LIFETIME = Duration.ofDays(60);
    private static final String INVALID_REFRESH = "Refresh token expired, invalid, or revoked. Please log in again.";

    @Value("${SUPERUSER_USERNAME}")
    private String superUsername;

    public AuthService(AppUserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       StringRedisTemplate redisTemplate,
                       PermissionChecker permissionChecker,
                       PermissionCacheEvictor permissionCacheEvictor) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
        this.permissionChecker = permissionChecker;
        this.permissionCacheEvictor = permissionCacheEvictor;
    }

    public TokenResponse login(LoginRequest request) {
        AppUser user = userRepository.findById(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Username or password is incorrect."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Username or password is incorrect.");
        }
        checkCanLogIn(user);
        List<String> estateIds = user.getAllowedEstates().stream()
                .map(Estate::getId)
                .toList();

        String accessToken = tokenProvider.generateToken(user.getUsername(), estateIds);
        String refreshToken = UUID.randomUUID().toString();

        try {
            redisTemplate.opsForValue().set(
                    "userEstates::" + user.getUsername(),
                    String.join(",", estateIds), // e.g. "ESTATE_A,ESTATE_B"
                    Duration.ofDays(1)
            );
        } catch (Exception e) {
            System.err.println("Valkey/Redis is down! Relying on JWT claims.");
        }

        refreshTokenRepository.deleteExpired(user.getUsername(), Instant.now());
        saveRefreshToken(user.getUsername(), refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse refresh(String username, String providedRefreshToken) {
        String owner = sessionOwner(providedRefreshToken);
        if (owner == null || !owner.equals(username)) {
            throw new InvalidCredentialsException(INVALID_REFRESH);
        }
        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        // An employee made inactive after login loses access at the next refresh
        checkCanLogIn(user);

        List<String> estateIds = user.getAllowedEstates().stream()
                .map(Estate::getId)
                .toList();

        // 2. Generate the new token WITH the estate claims
        String newAccessToken = tokenProvider.generateToken(username, estateIds);
        String newRefreshToken = UUID.randomUUID().toString();

        try {
            redisTemplate.opsForValue().set(
                    "userEstates::" + user.getUsername(),
                    String.join(",", estateIds), // e.g. "ESTATE_A,ESTATE_B"
                    Duration.ofDays(1)
            );
        } catch (Exception e) {
            System.err.println("Valkey/Redis is down! Relying on JWT claims.");
        }

        // Rotate only this session; the user's other sessions (web, another phone) carry on
        deleteSession(providedRefreshToken);
        saveRefreshToken(username, newRefreshToken);

        return new AuthDto.TokenResponse(newAccessToken, newRefreshToken);
    }

    // Web refresh: the HttpOnly cookie alone identifies the session, so the browser need not send a username.
    public TokenResponse refreshByToken(String providedRefreshToken) {
        String username = sessionOwner(providedRefreshToken);
        if (username == null) {
            throw new InvalidCredentialsException(INVALID_REFRESH);
        }
        return refresh(username, providedRefreshToken);
    }

    /**
     * LG-04: the employee behind the login must be Active. LG-05: the user must be granted at least
     * one permission set. The superuser has neither an employee nor grants and is always allowed.
     */
    private void checkCanLogIn(AppUser user) {
        if (user.getUsername().equals(superUsername)) {
            return;
        }
        Employee employee = user.getEmployee();
        if (employee != null && employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new InvalidCredentialsException("Your account is not active. Please contact the administrator.");
        }
        if (user.getPermissionSets().isEmpty()) {
            throw new InvalidCredentialsException(
                    "You do not have access to this application. Please contact the administrator.");
        }
    }

    /**
     * Ends the one session the refresh token belongs to (the web cookie or the phone's token). The
     * endpoint is open, so a username alone is not enough to end anyone's sessions.
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String username = sessionOwner(refreshToken);
        deleteSession(refreshToken);
        if (username != null) {
            permissionCacheEvictor.evictUser(username);
        }
    }

    /** Ends every session of the user, e.g. after a password change or when they are made Inactive. */
    public void revokeSessions(String username) {
        List<RefreshToken> sessions = refreshTokenRepository.findAllByUsername(username);
        try {
            sessions.forEach(session -> redisTemplate.delete(REDIS_PREFIX + session.getTokenHash()));
        } catch (Exception e) {
            System.err.println("Redis is down during logout! User session completely revoked from DB.");
        }
        refreshTokenRepository.deleteAllByUsername(username);
        permissionCacheEvictor.evictUser(username);
    }

    /** The user the refresh token belongs to, or null if it is unknown, expired or revoked. */
    private String sessionOwner(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String hash = hash(refreshToken);
        try {
            String username = redisTemplate.opsForValue().get(REDIS_PREFIX + hash);
            if (username != null) {
                return username;
            }
        } catch (Exception e) {
            System.err.println("Redis is down! Falling back to PostgreSQL for token check.");
        }
        return refreshTokenRepository.findById(hash)
                .filter(session -> session.getExpiryDate().isAfter(Instant.now()))
                .map(RefreshToken::getUsername)
                .orElse(null);
    }

    private void deleteSession(String refreshToken) {
        String hash = hash(refreshToken);
        refreshTokenRepository.deleteById(hash);
        try {
            redisTemplate.delete(REDIS_PREFIX + hash);
        } catch (Exception e) {
            System.err.println("Redis is down! Refresh token removed from DB only.");
        }
    }

    // Every token is also saved to Postgres, so checks work even when Redis is down
    private void saveRefreshToken(String username, String refreshToken) {
        String hash = hash(refreshToken);
        refreshTokenRepository.save(new RefreshToken(hash, username, Instant.now().plus(SESSION_LIFETIME)));
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + hash, username, SESSION_LIFETIME);
        } catch (Exception e) {
            System.err.println("Redis is down! Refresh token saved to DB only.");
        }
    }

    // Refresh tokens are random UUIDs, so a plain SHA-256 is enough to keep stored values unusable
    static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public CurrentUserProfileResponse getCurrentUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidCredentialsException("No access token provided.");
        }

        // Extract username using your existing token provider
        String username = tokenProvider.getUsernameFromJWT(accessToken); // Or whatever method your tokenProvider uses

        return getCurrentUserProfileByUsername(username);
    }

    public CurrentUserProfileResponse getCurrentUserProfileByUsername(String username) {
        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Employee employee = user.getEmployee();

        return new CurrentUserProfileResponse(
                user.getUsername(),
                employee == null ? null : employee.getFullName(),
                permissionChecker.isSuperuser(username),
                permissionChecker.grantedProjectionIds(username));
    }
}