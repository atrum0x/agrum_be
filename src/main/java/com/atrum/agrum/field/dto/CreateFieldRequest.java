package com.atrum.agrum.field.dto;

public record CreateFieldRequest(
        String fieldCode,
        String divisionCode,
        String description,
        Boolean isDefault
) {}
