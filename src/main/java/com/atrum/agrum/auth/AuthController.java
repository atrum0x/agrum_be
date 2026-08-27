package com.atrum.agrum.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            return ResponseEntity.ok("User registered successfully!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            TokenResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            TokenResponse response = authService.refresh(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok("Successfully logged out across all devices.");
    }

    // --------------------------------------------------------
    // DTOs (Data Transfer Objects)
    // --------------------------------------------------------

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