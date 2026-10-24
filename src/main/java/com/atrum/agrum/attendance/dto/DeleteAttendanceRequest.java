package com.atrum.agrum.attendance.dto;

public record DeleteAttendanceRequest(
        String reason,
        // The version the user opened, for AT-18
        Integer rowVersion
) {}
