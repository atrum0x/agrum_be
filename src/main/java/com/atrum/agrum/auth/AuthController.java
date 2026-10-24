package com.atrum.agrum.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.atrum.agrum.auth.AuthDto.*;
import com.atrum.agrum.exception.InvalidCredentialsException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookieDomain;

    public AuthController(AuthService authService,
                          @Value("${app.cookie.secure}") boolean cookieSecure,
                          @Value("${app.cookie.same-site}") String cookieSameSite,
                          @Value("${app.cookie.domain}") String cookieDomain) {
        // Browsers silently drop SameSite=None cookies that are not Secure, so fail fast instead
        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            throw new IllegalStateException("COOKIE_SAME_SITE=None requires COOKIE_SECURE=true");
        }
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully!");
    }

    // Web Login (Cookies only, no tokens in body)
    @PostMapping("/login/web")
    public ResponseEntity<?> loginWeb(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        // Returns the cookies, and just a simple string in the body
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Logged in securely from web");
    }

    // Mobile Login(Tokens in JSON body, no cookies)
    @PostMapping("/login/mobile")
    public ResponseEntity<TokenResponse> loginMobile(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        // Returns the tokens directly in the JSON payload for Flutter to save
        return ResponseEntity.ok(response);
    }

    // Web Refresh (Expects HttpOnly cookie, returns new HttpOnly cookies)
    @PostMapping("/refresh/web")
    public ResponseEntity<?> refreshWeb(
            @RequestBody RefreshRequest request,
            @CookieValue(name = "refresh_jwt", required = false) String refreshToken) {

        if (refreshToken == null) return ResponseEntity.status(401).body("No refresh token cookie found.");
        TokenResponse response = authService.refresh(request.getUsername(), refreshToken);
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Token refreshed");
    }

    // Mobile Refresh (Expects token in JSON body, returns tokens in JSON body)
    @PostMapping("/refresh/mobile")
    public ResponseEntity<TokenResponse> refreshMobile(@RequestBody RefreshRequest request) {
        // Note: Mobile passes the refresh token inside the JSON body, not as a cookie
        TokenResponse response = authService.refresh(request.getUsername(), request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // 1. WEB LOGOUT (Revokes session in DB + Clears Browser Cookies)
    @PostMapping("/logout/web")
    public ResponseEntity<?> logoutWeb(@RequestBody LogoutRequest request) {
        authService.logout(request);

        // Overwrite the cookies with empty values and a maxAge of 0 to delete them instantly
        // Attributes must match the original cookies or the browser will not delete them
        ResponseCookie deleteAccess = authCookie("access_jwt", "", 0);
        ResponseCookie deleteRefresh = authCookie("refresh_jwt", "", 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                .body("Successfully logged out from web.");
    }

    // 2. MOBILE LOGOUT (Revokes session in DB only)
    @PostMapping("/logout/mobile")
    public ResponseEntity<?> logoutMobile(@RequestBody LogoutRequest request) {
        authService.logout(request);

        // No cookies sent. The mobile app must delete its own stored tokens.
        return ResponseEntity.ok("Successfully logged out from mobile.");
    }

    @GetMapping("/me/web")
    public ResponseEntity<CurrentUserProfileResponse> getCurrentUser(
            @CookieValue(name = "access_jwt", required = false) String accessToken) {
        CurrentUserProfileResponse profile = authService.getCurrentUserProfile(accessToken);
        return ResponseEntity.ok(profile);
    }

    // The Bearer token is read by JwtAuthenticationFilter, as for every other call
    @GetMapping("/me/mobile")
    public ResponseEntity<CurrentUserProfileResponse> getCurrentUserMobile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InvalidCredentialsException("No access token provided.");
        }
        return ResponseEntity.ok(authService.getCurrentUserProfileByUsername(auth.getName()));
    }

    private ResponseEntity<?> buildCookieResponse(String accessToken, String refreshToken, String message) {
        ResponseCookie accessCookie = authCookie("access_jwt", accessToken, 15 * 60); // 15 minutes
        ResponseCookie refreshCookie = authCookie("refresh_jwt", refreshToken, 60 * 24 * 60 * 60); // 60 days

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(message); // We no longer send tokens in the body!
    }

    private ResponseCookie authCookie(String name, String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite);
        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }
}
