package com.atrum.agrum.division.dto;

/** divisionCode is the new code; it can change only while the division is unused (SB-12). Blank keeps it. */
public record UpdateDivisionRequest(
        String divisionCode,
        String description
) {}
