package com.atrum.agrum.attendance.dto;

import com.atrum.agrum.attendance.Attendance;
import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.attendance.AttendanceCorrection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceDto(
        UUID attendanceId,
        LocalDate attendanceDate,
        String employeeCode,
        String employeeName,
        String estateId,
        String estateName,
        String homeEstateId,
        String cpCode,
        String cpName,
        String workType,
        AttendanceCategory attendanceCategory,
        String recordedBy,
        Instant recordedAt,
        Instant receivedAt,
        String lastCorrectedBy,
        Instant lastCorrectedAt,
        Integer rowVersion
) {
    public static AttendanceDto from(Attendance attendance, AttendanceCorrection lastCorrection) {
        return new AttendanceDto(
                attendance.getAttendanceId(),
                attendance.getAttendanceDate(),
                attendance.getEmployee().getEmployeeCode(),
                attendance.getEmployee().getFullName(),
                attendance.getEstateId(),
                attendance.getEstate().getName(),
                attendance.getHomeEstate() == null ? null : attendance.getHomeEstate().getId(),
                attendance.getCollectionPoint().getCpCode(),
                attendance.getCollectionPoint().getName(),
                attendance.getWorkType().getWorkType(),
                attendance.getAttendanceCategory(),
                attendance.getRecordedBy().getUsername(),
                attendance.getRecordedAt(),
                attendance.getReceivedAt(),
                lastCorrection == null ? null : lastCorrection.getCorrectedBy().getUsername(),
                lastCorrection == null ? null : lastCorrection.getCorrectedAt(),
                attendance.getRowVersion());
    }
}
