package com.atrum.agrum.workType.dto;

public record CreateWorkTypeRequest(
        String workTypeCode,
        String name,
        Boolean weighingRequired
) {}
