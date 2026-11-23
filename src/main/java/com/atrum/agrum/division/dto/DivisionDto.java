package com.atrum.agrum.division.dto;

import com.atrum.agrum.division.Division;

public record DivisionDto(
        String divisionCode,
        String estateId,
        String description
) {
    public static DivisionDto from(Division division) {
        return new DivisionDto(division.getDivisionCode(), division.getEstateId(), division.getDescription());
    }
}
