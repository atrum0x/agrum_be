package com.atrum.agrum.workType.dto;

import com.atrum.agrum.workType.WorkType;

public record WorkTypeDto(
        String workType,
        String estateId,
        boolean weighingRequired
) {
    public static WorkTypeDto from(WorkType workType) {
        return new WorkTypeDto(
                workType.getWorkType(),
                workType.getEstateId(),
                workType.isWeighingRequired());
    }
}
