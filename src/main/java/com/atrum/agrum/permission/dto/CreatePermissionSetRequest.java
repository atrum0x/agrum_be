package com.atrum.agrum.permission.dto;

public record CreatePermissionSetRequest(
        String permissionSetId,
        String description
) {
}
