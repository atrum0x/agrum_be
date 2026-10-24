package com.atrum.agrum.attendance.dto;

import java.time.LocalDate;

/**
 * List filters. Dates default to today; blank filters are ignored.
 */
public record AttendanceSearchRequest(
        LocalDate dateFrom,
        LocalDate dateTo,
        // EMP No or name
        String employee,
        String estateId,
        String cpCode,
        String workType,
        String recordedBy
) {}
