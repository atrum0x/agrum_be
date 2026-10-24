package com.atrum.agrum.weight.dto;

import java.time.Instant;
import java.util.List;

public record DailyWeightSummaryResultDto(
        // Start of the last successful 10 pm run, shown at the top of the screen; null before the first run
        Instant lastCalculatedAt,
        List<DailyWeightSummaryDto> rows
) {}
