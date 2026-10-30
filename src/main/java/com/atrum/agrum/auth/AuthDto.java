package com.atrum.agrum.auth;

import lombok.Getter;

import java.util.List;

public class AuthDto {

    @Getter
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Getter
    public static class RefreshRequest {
        private String username;
        private String refreshToken;
    }

    @Getter
    public static class LogoutRequest {
        private String username;
        // The session to end. The web sends it as the refresh_jwt cookie instead
        private String refreshToken;
    }

    /** Sets up fingerprint login on a phone: the password again, and the Keystore key's public half. */
    @Getter
    public static class BiometricRegisterRequest {
        private String username;
        private String password;
        // X.509 SubjectPublicKeyInfo, base64, EC P-256
        private String publicKey;
    }

    @Getter
    public static class BiometricRegisterResponse {
        private final String keyId;

        public BiometricRegisterResponse(String keyId) {
            this.keyId = keyId;
        }
    }

    @Getter
    public static class BiometricKeyRequest {
        private String keyId;
    }

    @Getter
    public static class BiometricChallengeResponse {
        // The phone signs "agrum-login:<keyId>:<challenge>"; usable once, for 2 minutes
        private final String challenge;

        public BiometricChallengeResponse(String challenge) {
            this.challenge = challenge;
        }
    }

    @Getter
    public static class BiometricLoginRequest {
        private String keyId;
        // SHA256withECDSA, DER, base64
        private String signature;
    }

    @Getter
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;

        public TokenResponse(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    /**
     * The logged-in user and what they are granted, so the web menu and the phone's Mobile Home show
     * only the windows and buttons the user may use (DB-01, LG-05). The server still checks every call.
     * permissions are projection ids such as "GET:/api/weights/mine"; the superuser gets an empty
     * list with superuser = true.
     */
    @Getter
    public static class CurrentUserProfileResponse {
        private final String username;
        private final String employeeName;
        private final boolean superuser;
        private final List<String> permissions;

        public CurrentUserProfileResponse(String username, String employeeName, boolean superuser,
                                          List<String> permissions) {
            this.username = username;
            this.employeeName = employeeName;
            this.superuser = superuser;
            this.permissions = permissions;
        }
    }
}
