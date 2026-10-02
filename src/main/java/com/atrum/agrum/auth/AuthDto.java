package com.atrum.agrum.auth;

import lombok.Getter;
import lombok.Setter;

public class AuthDto {

    @Setter
    @Getter
    public static class RegisterRequest {
        private String username;
        private String password;

    }

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

    @Getter
    public static class CurrentUserProfileResponse {
        private String username;
        public CurrentUserProfileResponse(String username) {
            this.username = username;
        }

    }
}
