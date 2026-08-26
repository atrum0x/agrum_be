package com.atrum.agrum.auth;

import com.atrum.agrum.security.JwtTokenProvider;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.AppUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate; // Autoconfigured by Spring Boot

    public AuthController(AppUserRepository userRepository,
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

    // --------------------------------------------------------
    // 0. REGISTER (Create a new AppUser)
    // --------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // 1. Check if username is already taken
        if (userRepository.existsById(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username is already taken!");
        }

        // 2. Create the user entity & hash password using BCrypt
        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // Crucial: Never store raw passwords!

        // 3. Save to PostgreSQL / Oracle
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    // --------------------------------------------------------
    // 1. LOGIN (Issue Access + Refresh Token)
    // --------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        AppUser user = userRepository.findById(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        // Generate 15-minute Access Token (JWT)
        String accessToken = tokenProvider.generateToken(user.getUsername());

        // Generate 60-day Refresh Token and save to Redis
        String refreshToken = UUID.randomUUID().toString();
        RefreshToken dbToken = new RefreshToken(
                user.getUsername(),
                refreshToken,
                Instant.now().plus(Duration.ofDays(60))
        );
        refreshTokenRepository.save(dbToken);
        try {
            redisTemplate.opsForValue().set(
                    "refresh_tokens:" + user.getUsername(),
                    refreshToken,
                    Duration.ofDays(60)
            );
        } catch (Exception e) {
            System.err.println("Redis is down! Refresh token saved to DB only.");
        }

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    // --------------------------------------------------------
    // 2. REFRESH (Silent Renewal for Mobile/Web)
    // --------------------------------------------------------
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        String username = request.getUsername();
        String providedRefreshToken = request.getRefreshToken();

        String storedToken = null;
        // Check Redis to see if the token exists and matches
        try {
            // Attempt fast read from Redis
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
            return ResponseEntity.status(401).body("Refresh token expired, invalid, or revoked. Please log in again.");
        }

        // Token is valid! Issue fresh 15-minute Access Token
        String newAccessToken = tokenProvider.generateToken(username);

        // Security Best Practice: Rotate the refresh token (Issue a new one, delete the old one)
        String newRefreshToken = UUID.randomUUID().toString();

        RefreshToken dbToken = new RefreshToken(
                username,
                newRefreshToken,
                Instant.now().plus(Duration.ofDays(60))
        );
        refreshTokenRepository.save(dbToken);

        try {
            redisTemplate.opsForValue().set(
                    "refresh_tokens:" + username,
                    newRefreshToken,
                    Duration.ofDays(60)
            );
        } catch (Exception e) {
            System.err.println("Redis is down during token rotation! Token updated in DB only.");
        }

        return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken));
    }

    // --------------------------------------------------------
    // 3. LOGOUT (Instant Revocation)
    // --------------------------------------------------------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest request) {
        String username = request.getUsername();

        // 1. PRIMARY: Delete from PostgreSQL so the token is permanently revoked
        refreshTokenRepository.deleteById(username);

        // 2. CACHE: Try to clear Redis caches
        try {
            // Delete the refresh token so the mobile app can no longer renew its session
            redisTemplate.delete("refresh_tokens:" + username);

            // Force clear the user's permission cache so if their permissions
            // changed while logged out, the next login fetches fresh data.
            redisTemplate.delete("userPermissions::" + username + "_GET");
            redisTemplate.delete("userPermissions::" + username + "_POST");
            redisTemplate.delete("userPermissions::" + username + "_PUT");
            redisTemplate.delete("userPermissions::" + username + "_DELETE");
        } catch (Exception e) {
            System.err.println("Redis is down during logout! User session completely revoked from DB.");
        }

        return ResponseEntity.ok("Successfully logged out across all devices.");
    }

    // --- DTOs (Data Transfer Objects) ---

    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginRequest {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    public static class RefreshRequest {
        private String username;
        private String refreshToken;
        public String getUsername() { return username; }
        public String getRefreshToken() { return refreshToken; }
    }

    public static class LogoutRequest {
        private String username;
        public String getUsername() { return username; }
    }

    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;

        public TokenResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
    }
}