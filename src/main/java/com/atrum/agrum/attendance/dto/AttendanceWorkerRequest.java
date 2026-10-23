package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.AttendanceCategory;

public record AttendanceWorkerRequest(
        String employeeCode,
        AttendanceCategory attendanceCategory
) {}
