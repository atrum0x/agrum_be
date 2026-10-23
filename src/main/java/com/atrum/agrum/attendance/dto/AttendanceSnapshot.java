package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.Attendance;
import com.atrum.agrum.attendance.AttendanceCategory;

import java.time.LocalDate;

/**
 * The values stored as JSON in attendance_corrections.old_values / new_values.
 */
public record AttendanceSnapshot(
        String employeeCode,
        LocalDate attendanceDate,
        String estateId,
        String workType,
        String cpCode,
        AttendanceCategory attendanceCategory
) {
    public static AttendanceSnapshot of(Attendance attendance) {
        return new AttendanceSnapshot(
                attendance.getEmployee().getEmployeeCode(),
                attendance.getAttendanceDate(),
                attendance.getEstateId(),
                attendance.getWorkType().getWorkType(),
                attendance.getCollectionPoint().getCpCode(),
                attendance.getAttendanceCategory());
    }
}
