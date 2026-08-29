package com.atrum.agrum.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ApiErrorResponse {
    // Getters and Setters
    private final int status;
    private final String message;
    private final LocalDateTime timestamp;

    public ApiErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

}