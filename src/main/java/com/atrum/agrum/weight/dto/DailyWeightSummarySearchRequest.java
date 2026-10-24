package com.atrum.agrum.weight.dto;

import java.time.LocalDate;

/**
 * Daily Weight Summary filters. Dates default to yesterday (proposed); blank filters are ignored.
 */
public record DailyWeightSummarySearchRequest(
        LocalDate dateFrom,
        LocalDate dateTo,
        String employeeCode,
        String estateId,
        String cpCode,
        String workType
) {}
