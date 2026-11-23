package com.atrum.agrum.workType.dto;

import com.atrum.agrum.workType.WorkType;

public record WorkTypeDto(
        String workTypeCode,
        String name,
        String estateId,
        boolean weighingRequired
) {
    public static WorkTypeDto from(WorkType workType) {
        return new WorkTypeDto(
                workType.getWorkTypeCode(),
                workType.getName(),
                workType.getEstateId(),
                workType.isWeighingRequired());
    }
}
