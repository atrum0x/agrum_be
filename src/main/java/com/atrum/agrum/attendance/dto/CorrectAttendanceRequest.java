package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.AttendanceCategory;

/**
 * Worker and date are never correctable; delete and re-record instead.
 */
public record CorrectAttendanceRequest(
        String estateId,
        String workType,
        String cpCode,
        AttendanceCategory attendanceCategory,
        String reason,
        // The version the user opened, for AT-18
        Integer rowVersion
) {}
