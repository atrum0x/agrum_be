package com.atrum.agrum.field.dto;

/**
 * fieldCode is the new code; it can change only while the field is unused (SB-12). Blank keeps it.
 * divisionCode moves the field to another division of the site; blank keeps it. A null isDefault
 * keeps the flag.
 */
public record UpdateFieldRequest(
        String fieldCode,
        String divisionCode,
        String description,
        Boolean isDefault
) {}
