package com.atrum.agrum.workType.dto;

/** The work type code is the key and is not updatable. */
public record UpdateWorkTypeRequest(
        Boolean weighingRequired
) {}
