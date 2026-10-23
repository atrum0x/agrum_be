package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.AttendanceCategory;

/**
 * A worker that can be ticked on the record screen, with the defaults the screen pre-fills.
 */
public record AttendanceWorkerDto(
        String employeeCode,
        String fullName,
        String defaultEstateId,
        String homeEstateId,
        String defaultCpCode,
        // Null when the employee's category text is not one of the attendance categories
        AttendanceCategory defaultCategory
) {}
