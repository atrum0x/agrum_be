package com.atrum.agrum.weight.dto;

import com.atrum.agrum.weight.ApprovalStatus;
import com.atrum.agrum.weight.ChangeType;
import com.atrum.agrum.weight.WeightCalculator;
import com.atrum.agrum.weight.WeightChangeRequest;
import com.atrum.agrum.weight.WeightRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A correction or cancellation request, with the weighing it is for.
 * Used for View History and for the approver's pending list.
 */
public record WeightChangeDto(
        Long changeId,
        UUID weightId,
        LocalDate weighDate,
        int entrySequence,
        String employeeCode,
        String employeeName,
        String estateId,
        ChangeType changeType,
        BigDecimal previousWeightKg,
        // Null for a cancellation
        BigDecimal proposedWeightKg,
        // Preview with the weighing's saved variance; null for a cancellation
        BigDecimal proposedWeightAfterVariance,
        BigDecimal variancePercent,
        String comment,
        ApprovalStatus requestStatus,
        String requestedBy,
        Instant requestedAt,
        String decidedBy,
        Instant decidedAt,
        // The weighing's version, sent back with Approve / Disapprove (WH-10)
        Integer weightRowVersion
) {
    public static WeightChangeDto from(WeightChangeRequest request) {
        WeightRecord weight = request.getWeight();
        return new WeightChangeDto(
                request.getId(),
                weight.getWeightId(),
                weight.getWeighDate(),
                weight.getEntrySequence(),
                weight.getEmployee().getEmployeeCode(),
                weight.getEmployee().getFullName(),
                weight.getEstateId(),
                request.getChangeType(),
                request.getPreviousWeightKg(),
                request.getProposedWeightKg(),
                request.getProposedWeightKg() == null ? null
                        : WeightCalculator.afterVariance(request.getProposedWeightKg(), weight.getVariancePercent()),
                weight.getVariancePercent(),
                request.getComment(),
                request.getRequestStatus(),
                request.getRequestedBy().getUsername(),
                request.getRequestedAt(),
                request.getDecidedBy() == null ? null : request.getDecidedBy().getUsername(),
                request.getDecidedAt(),
                weight.getRowVersion());
    }
}
