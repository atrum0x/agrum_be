package com.atrum.agrum.attendance.dto;

import java.util.List;

public record SyncAttendanceRequest(
        List<SyncAttendanceRecord> records
) {}
