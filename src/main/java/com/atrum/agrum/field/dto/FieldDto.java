package com.atrum.agrum.field.dto;

import com.atrum.agrum.field.Field;

public record FieldDto(
        String fieldCode,
        String estateId,
        String divisionCode,
        String description,
        boolean isDefault
) {
    public static FieldDto from(Field field) {
        return new FieldDto(
                field.getFieldCode(),
                field.getEstateId(),
                field.getDivisionCode(),
                field.getDescription(),
                field.isDefault());
    }
}
