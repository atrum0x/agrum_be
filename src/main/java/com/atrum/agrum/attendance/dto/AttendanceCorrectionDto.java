package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.CorrectionAction;

import java.time.Instant;
import java.util.UUID;

public record AttendanceCorrectionDto(
        Long id,
        UUID attendanceId,
        CorrectionAction action,
        AttendanceSnapshot oldValues,
        // Null for Delete
        AttendanceSnapshot newValues,
        String reason,
        String correctedBy,
        Instant correctedAt
) {}
