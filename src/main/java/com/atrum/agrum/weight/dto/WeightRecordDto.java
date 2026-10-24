package com.atrum.agrum.weight.dto;

import com.atrum.agrum.weight.ApprovalStatus;
import com.atrum.agrum.weight.EntryType;
import com.atrum.agrum.weight.LineState;
import com.atrum.agrum.weight.WeightRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeightRecordDto(
        UUID weightId,
        String employeeCode,
        String employeeName,
        LocalDate weighDate,
        Instant weighedAt,
        int entrySequence,
        String estateId,
        String cpCode,
        String workType,
        EntryType entryType,
        BigDecimal originalWeightKg,
        BigDecimal weightKg,
        BigDecimal variancePercent,
        BigDecimal weightAfterVariance,
        LineState lineState,
        ApprovalStatus approvalStatus,
        String supervisor,
        Instant receivedAt,
        // Sent back with a change decision (WH-10)
        Integer rowVersion
) {
    public static WeightRecordDto from(WeightRecord record) {
        return new WeightRecordDto(
                record.getWeightId(),
                record.getEmployee().getEmployeeCode(),
                record.getEmployee().getFullName(),
                record.getWeighDate(),
                record.getWeighedAt(),
                record.getEntrySequence(),
                record.getEstateId(),
                record.getCollectionPoint().getCpCode(),
                record.getWorkType().getWorkType(),
                record.getEntryType(),
                record.getOriginalWeightKg(),
                record.getWeightKg(),
                record.getVariancePercent(),
                record.getWeightAfterVariance(),
                record.getLineState(),
                record.getApprovalStatus(),
                record.getSupervisor().getUsername(),
                record.getReceivedAt(),
                record.getRowVersion());
    }
}
