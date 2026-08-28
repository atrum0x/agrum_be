package com.atrum.agrum.auth;

import com.atrum.agrum.security.JwtTokenProvider;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {


    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    public AuthService(AppUserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
    }

    public void register(AuthDto.RegisterRequest request) {
        if (userRepository.existsById(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
    }

    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        AppUser user = userRepository.findById(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = tokenProvider.generateToken(user.getUsername());
        String refreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user.getUsername(), refreshToken);

        return new AuthDto.TokenResponse(accessToken, refreshToken);
    }

    public AuthDto.TokenResponse refresh(String username, String providedRefreshToken) {
        String storedToken = null;

        try {
            storedToken = redisTemplate.opsForValue().get("refresh_tokens:" + username);
        } catch (Exception e) {
            System.err.println("Redis is down! Falling back to PostgreSQL for token check.");
        }

        if (storedToken == null) {
            storedToken = refreshTokenRepository.findById(username)
                    .filter(rt -> rt.getExpiryDate().isAfter(Instant.now()))
                    .map(RefreshToken::getToken)
                    .orElse(null);
        }

        if (storedToken == null || !storedToken.equals(providedRefreshToken)) {
            throw new RuntimeException("Refresh token expired, invalid, or revoked. Please log in again.");
        }

        String newAccessToken = tokenProvider.generateToken(username);
        String newRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(username, newRefreshToken);

        return new AuthDto.TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(AuthDto.LogoutRequest request) {
        String username = request.getUsername();

        refreshTokenRepository.deleteById(username);

        try {
            redisTemplate.delete("refresh_tokens:" + username);
            redisTemplate.delete("userPermissions::" + username + "_GET");
            redisTemplate.delete("userPermissions::" + username + "_POST");
            redisTemplate.delete("userPermissions::" + username + "_PUT");
            redisTemplate.delete("userPermissions::" + username + "_DELETE");
        } catch (Exception e) {
            System.err.println("Redis is down during logout! User session completely revoked from DB.");
        }
    }

    // Extracted helper method to keep code DRY (Don't Repeat Yourself)
    private void saveRefreshToken(String username, String refreshToken) {
        RefreshToken dbToken = new RefreshToken(
                username,
                refreshToken,
                Instant.now().plus(Duration.ofDays(60))
        );
        refreshTokenRepository.save(dbToken);

        try {
            redisTemplate.opsForValue().set(
                    "refresh_tokens:" + username,
                    refreshToken,
                    Duration.ofDays(60)
            );
        } catch (Exception e) {
            System.err.println("Redis is down! Refresh token saved to DB only.");
        }
    }

    public AuthDto.CurrentUserProfileResponse getCurrentUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("No access token provided.");
        }

        // Extract username using your existing token provider
        String username = tokenProvider.getUsernameFromJWT(accessToken); // Or whatever method your tokenProvider uses

        AppUser user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new AuthDto.CurrentUserProfileResponse(user.getUsername(), user.getEmail());
    }
}