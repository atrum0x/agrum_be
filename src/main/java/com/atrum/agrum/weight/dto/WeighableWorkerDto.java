package com.atrum.agrum.weight.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A worker that can be weighed today, with everything the weighing screen fills in from the attendance.
 */
public record WeighableWorkerDto(
        String employeeCode,
        String fullName,
        UUID attendanceId,
        String estateId,
        String estateName,
        String divisionCode,
        String divisionDescription,
        String fieldCode,
        String fieldDescription,
        String workType,
        String workTypeName,
        // The site's current variance; the server applies its own value on save (CL-02)
        BigDecimal variancePercent,
        // "Entries Today": entriesToday of dailyLimit; Save is blocked at the limit (WE-04)
        int entriesToday,
        int dailyLimit,
        // Highest entry sequence so far, including cancelled; the phone's provisional number is this + 1
        int lastSequence
) {}
