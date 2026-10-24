package com.atrum.agrum.attendance.dto;

import java.util.UUID;

public record SyncAttendanceResult(
        UUID attendanceId,
        SyncStatus status,
        // Shown on the phone next to a rejected record
        String reason
) {
    public static SyncAttendanceResult sent(UUID attendanceId) {
        return new SyncAttendanceResult(attendanceId, SyncStatus.SENT, null);
    }

    public static SyncAttendanceResult rejected(UUID attendanceId, String reason) {
        return new SyncAttendanceResult(attendanceId, SyncStatus.REJECTED, reason);
    }
}
