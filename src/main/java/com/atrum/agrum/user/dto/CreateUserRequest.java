package com.atrum.agrum.user.dto;

public record CreateUserRequest(
        String employeeCode,
        String password
) {
}
