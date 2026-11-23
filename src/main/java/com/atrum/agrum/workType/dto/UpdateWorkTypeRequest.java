package com.atrum.agrum.workType.dto;

/** The Work Type ID is the key and is not updatable (SB-16). */
public record UpdateWorkTypeRequest(
        String name,
        Boolean weighingRequired
) {}
