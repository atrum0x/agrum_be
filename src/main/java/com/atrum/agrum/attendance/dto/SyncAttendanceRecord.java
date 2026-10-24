package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.AttendanceCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One attendance row saved on a phone, sent when it is back online.
 */
public record SyncAttendanceRecord(
        // Created on the phone; a resend with the same id is accepted as already received (AT-12)
        UUID attendanceId,
        String employeeCode,
        String estateId,
        String workType,
        String cpCode,
        AttendanceCategory attendanceCategory,
        LocalDate attendanceDate,
        Instant recordedAt
) {}
