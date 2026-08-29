package com.atrum.agrum.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully!");
    }

    // Web Login (Cookies only, no tokens in body)
    @PostMapping("/login/web")
    public ResponseEntity<?> loginWeb(@RequestBody AuthDto.LoginRequest request) {
        AuthDto.TokenResponse response = authService.login(request);
        // Returns the cookies, and just a simple string in the body
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Logged in securely from web");
    }

    // Mobile Login(Tokens in JSON body, no cookies)
    @PostMapping("/login/mobile")
    public ResponseEntity<?> loginMobile(@RequestBody AuthDto.LoginRequest request) {
        AuthDto.TokenResponse response = authService.login(request);
        // Returns the tokens directly in the JSON payload for Flutter to save
        return ResponseEntity.ok(response);
    }

    // Web Refresh (Expects HttpOnly cookie, returns new HttpOnly cookies)
    @PostMapping("/refresh/web")
    public ResponseEntity<?> refreshWeb(
            @RequestBody AuthDto.RefreshRequest request,
            @CookieValue(name = "refresh_jwt", required = false) String refreshToken) {

        if (refreshToken == null) return ResponseEntity.status(401).body("No refresh token cookie found.");
        AuthDto.TokenResponse response = authService.refresh(request.getUsername(), refreshToken);
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Token refreshed");
    }

    // Mobile Refresh (Expects token in JSON body, returns tokens in JSON body)
    @PostMapping("/refresh/mobile")
    public ResponseEntity<?> refreshMobile(@RequestBody AuthDto.RefreshRequest request) {
        // Note: Mobile passes the refresh token inside the JSON body, not as a cookie
        AuthDto.TokenResponse response = authService.refresh(request.getUsername(), request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // 1. WEB LOGOUT (Revokes session in DB + Clears Browser Cookies)
    @PostMapping("/logout/web")
    public ResponseEntity<?> logoutWeb(@RequestBody AuthDto.LogoutRequest request) {
        authService.logout(request);

        // Overwrite the cookies with empty values and a maxAge of 0 to delete them instantly
        ResponseCookie deleteAccess = ResponseCookie.from("access_jwt", "").maxAge(0).path("/").build();
        ResponseCookie deleteRefresh = ResponseCookie.from("refresh_jwt", "").maxAge(0).path("/").build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                .body("Successfully logged out from web.");
    }

    // 2. MOBILE LOGOUT (Revokes session in DB only)
    @PostMapping("/logout/mobile")
    public ResponseEntity<?> logoutMobile(@RequestBody AuthDto.LogoutRequest request) {
        authService.logout(request);

        // No cookies sent. The mobile app must delete its own stored tokens.
        return ResponseEntity.ok("Successfully logged out from mobile.");
    }

    @GetMapping("/me/web")
    public ResponseEntity<?> getCurrentUser(
            @CookieValue(name = "access_jwt", required = false) String accessToken) {
        AuthDto.CurrentUserProfileResponse profile = authService.getCurrentUserProfile(accessToken);
        return ResponseEntity.ok(profile);
    }

    private ResponseEntity<?> buildCookieResponse(String accessToken, String refreshToken, String message) {
        ResponseCookie accessCookie = ResponseCookie.from("access_jwt", accessToken)
                .httpOnly(true)
                .secure(false) // TODO: Set to true in production with HTTPS
                .path("/")
                .maxAge(15 * 60) // 15 minutes
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_jwt", refreshToken)
                .httpOnly(true)
                .secure(false) // TODO: Set to true in production
                .path("/")
                .maxAge(60 * 24 * 60 * 60) // 60 days
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(message); // We no longer send tokens in the body!
    }
}