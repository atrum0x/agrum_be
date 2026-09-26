package com.atrum.agrum.permission.dto;

public record ProjectionDto(
        String id,
        String httpMethod,
        String urlPath,
        String description
) {}
