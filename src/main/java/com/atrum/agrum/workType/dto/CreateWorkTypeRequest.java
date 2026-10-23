package com.atrum.agrum.workType.dto;

public record CreateWorkTypeRequest(
        String workType,
        Boolean weighingRequired
) {}
