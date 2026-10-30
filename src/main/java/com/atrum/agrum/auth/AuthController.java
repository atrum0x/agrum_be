package com.atrum.agrum.auth;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import com.atrum.agrum.auth.AuthDto.*;
import com.atrum.agrum.exception.InvalidCredentialsException;

@ProjectionGroup("Login (always allowed, no grant needed)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final BiometricLoginService biometricLoginService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookieDomain;
    // LG-06 / CL-07: a web session ends after this long without activity. Each refresh (about every
    // 15 minutes while the user is active) starts it again
    private final Duration webSessionIdle;

    public AuthController(AuthService authService,
                          BiometricLoginService biometricLoginService,
                          @Value("${app.cookie.secure}") boolean cookieSecure,
                          @Value("${app.cookie.same-site}") String cookieSameSite,
                          @Value("${app.cookie.domain}") String cookieDomain,
                          @Value("${app.session.web-idle-minutes:30}") long webSessionIdleMinutes) {
        // Browsers silently drop SameSite=None cookies that are not Secure, so fail fast instead
        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            throw new IllegalStateException("COOKIE_SAME_SITE=None requires COOKIE_SECURE=true");
        }
        this.authService = authService;
        this.biometricLoginService = biometricLoginService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
        this.webSessionIdle = Duration.ofMinutes(webSessionIdleMinutes);
    }

    // Web Login (Cookies only, no tokens in body)
    @Operation(summary = "Log in to the web app")
    @PostMapping("/login/web")
    public ResponseEntity<?> loginWeb(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request, webSessionIdle);
        // Returns the cookies, and just a simple string in the body
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Logged in securely from web");
    }

    // Mobile Login(Tokens in JSON body, no cookies)
    @Operation(summary = "Log in to the mobile app")
    @PostMapping("/login/mobile")
    public ResponseEntity<TokenResponse> loginMobile(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        // Returns the tokens directly in the JSON payload for Flutter to save
        return ResponseEntity.ok(response);
    }

    // Fingerprint login (phone only): set up with the password once, then sign a one-time challenge
    @Operation(summary = "Set up fingerprint login on a phone")
    @PostMapping("/biometric/register/mobile")
    public ResponseEntity<BiometricRegisterResponse> registerBiometric(@RequestBody BiometricRegisterRequest request) {
        String keyId = biometricLoginService.register(request.getUsername(), request.getPassword(), request.getPublicKey());
        return ResponseEntity.ok(new BiometricRegisterResponse(keyId));
    }

    @Operation(summary = "Get a one-time challenge for fingerprint login")
    @PostMapping("/biometric/challenge/mobile")
    public ResponseEntity<BiometricChallengeResponse> biometricChallenge(@RequestBody BiometricKeyRequest request) {
        return ResponseEntity.ok(new BiometricChallengeResponse(biometricLoginService.challenge(request.getKeyId())));
    }

    @Operation(summary = "Log in to the mobile app with a fingerprint")
    @PostMapping("/login/biometric/mobile")
    public ResponseEntity<TokenResponse> loginBiometric(@RequestBody BiometricLoginRequest request) {
        return ResponseEntity.ok(biometricLoginService.login(request.getKeyId(), request.getSignature()));
    }

    @Operation(summary = "Turn off fingerprint login on a phone")
    @PostMapping("/biometric/remove/mobile")
    public ResponseEntity<?> removeBiometric(@RequestBody BiometricKeyRequest request) {
        biometricLoginService.remove(request.getKeyId());
        return ResponseEntity.ok("Fingerprint login turned off.");
    }

    // Web Refresh (Expects HttpOnly cookie, returns new HttpOnly cookies)
    @Operation(summary = "Keep a web session alive")
    @PostMapping("/refresh/web")
    public ResponseEntity<?> refreshWeb(
            @CookieValue(name = "refresh_jwt", required = false) String refreshToken) {

        if (refreshToken == null) throw new InvalidCredentialsException("No refresh token cookie found.");
        TokenResponse response = authService.refreshByToken(refreshToken, webSessionIdle);
        return buildCookieResponse(response.getAccessToken(), response.getRefreshToken(), "Token refreshed");
    }

    // Mobile Refresh (Expects token in JSON body, returns tokens in JSON body)
    @Operation(summary = "Keep a mobile session alive")
    @PostMapping("/refresh/mobile")
    public ResponseEntity<TokenResponse> refreshMobile(@RequestBody RefreshRequest request) {
        // Note: Mobile passes the refresh token inside the JSON body, not as a cookie
        TokenResponse response = authService.refresh(request.getUsername(), request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // 1. WEB LOGOUT (Revokes session in DB + Clears Browser Cookies)
    @Operation(summary = "Log out of the web app")
    @PostMapping("/logout/web")
    public ResponseEntity<?> logoutWeb(@CookieValue(name = "refresh_jwt", required = false) String refreshToken) {
        // Ends this browser's session only; the user's other sessions stay logged in
        authService.logout(refreshToken);

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
    @Operation(summary = "Log out of the mobile app")
    @PostMapping("/logout/mobile")
    public ResponseEntity<?> logoutMobile(@RequestBody LogoutRequest request) {
        // Ends this phone's session only
        authService.logout(request.getRefreshToken());

        // No cookies sent. The mobile app must delete its own stored tokens.
        return ResponseEntity.ok("Successfully logged out from mobile.");
    }

    @Operation(summary = "Current web user and what they are granted")
    @GetMapping("/me/web")
    public ResponseEntity<CurrentUserProfileResponse> getCurrentUser(
            @CookieValue(name = "access_jwt", required = false) String accessToken) {
        CurrentUserProfileResponse profile = authService.getCurrentUserProfile(accessToken);
        return ResponseEntity.ok(profile);
    }

    // The Bearer token is read by JwtAuthenticationFilter, as for every other call
    @Operation(summary = "Current mobile user and what they are granted")
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
        ResponseCookie refreshCookie = authCookie("refresh_jwt", refreshToken, webSessionIdle.toSeconds());

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
