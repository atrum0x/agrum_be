package com.atrum.agrum.projection.dto;

public record ProjectionDto(
        String id,
        String httpMethod,
        String urlPath,
        String description
) {}
