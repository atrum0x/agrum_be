package com.atrum.agrum.weight.dto;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.weight.ApprovalStatus;
import com.atrum.agrum.weight.ChangeType;
import com.atrum.agrum.weight.EntryType;
import com.atrum.agrum.weight.IdentifiedBy;
import com.atrum.agrum.weight.LineState;
import com.atrum.agrum.weight.WeightChangeRequest;
import com.atrum.agrum.weight.WeightRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Weight History row. The change columns describe the latest request and are null
 * when the weighing was never changed (shown as "—").
 */
public record WeightHistoryRowDto(
        UUID weightId,
        LocalDate weighDate,
        Instant weighedAt,
        int entrySequence,
        String employeeCode,
        String employeeName,
        AttendanceCategory attendanceCategory,
        String supervisor,
        String supervisorName,
        String estateId,
        String estateName,
        String cpCode,
        String cpName,
        String workType,
        EntryType entryType,
        // How the phone picked the worker; null for older weighings. FACE rows may have a photo
        IdentifiedBy identifiedBy,
        BigDecimal faceSimilarity,
        BigDecimal originalWeightKg,
        // Weight in use
        BigDecimal weightKg,
        BigDecimal variancePercent,
        BigDecimal weightAfterVariance,
        LineState lineState,
        ApprovalStatus approvalStatus,
        Long changeId,
        ChangeType changeType,
        ApprovalStatus changeStatus,
        BigDecimal proposedWeightKg,
        String changeComment,
        String requestedBy,
        Instant requestedAt,
        String decidedBy,
        Instant decidedAt,
        // Sent back with a change request (WH-10)
        Integer rowVersion
) {
    public static WeightHistoryRowDto from(WeightRecord record, WeightChangeRequest latest) {
        return new WeightHistoryRowDto(
                record.getWeightId(),
                record.getWeighDate(),
                record.getWeighedAt(),
                record.getEntrySequence(),
                record.getEmployee().getEmployeeCode(),
                record.getEmployee().getFullName(),
                record.getAttendance().getAttendanceCategory(),
                record.getSupervisor().getUsername(),
                displayName(record.getSupervisor()),
                record.getEstateId(),
                record.getEstate().getName(),
                record.getCollectionPoint().getCpCode(),
                record.getCollectionPoint().getName(),
                record.getWorkType().getWorkType(),
                record.getEntryType(),
                record.getIdentifiedBy(),
                record.getFaceSimilarity(),
                record.getOriginalWeightKg(),
                record.getWeightKg(),
                record.getVariancePercent(),
                record.getWeightAfterVariance(),
                record.getLineState(),
                record.getApprovalStatus(),
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getChangeType(),
                latest == null ? null : latest.getRequestStatus(),
                latest == null ? null : latest.getProposedWeightKg(),
                latest == null ? null : latest.getComment(),
                latest == null ? null : latest.getRequestedBy().getUsername(),
                latest == null ? null : latest.getRequestedAt(),
                latest == null || latest.getDecidedBy() == null ? null : latest.getDecidedBy().getUsername(),
                latest == null ? null : latest.getDecidedAt(),
                record.getRowVersion());
    }

    // The supervisor's employee name, or the username for accounts without an employee
    private static String displayName(AppUser user) {
        return user.getEmployee() == null ? user.getUsername() : user.getEmployee().getFullName();
    }
}
