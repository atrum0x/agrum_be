package com.atrum.agrum.weight.dto;

import com.atrum.agrum.weight.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One weighing saved on a phone. Sent on its own when online, or in a sync batch.
 * Site, collection point and work type are not sent: they come from the attendance.
 */
public record RecordWeightRequest(
        // Created on the phone; a resend with the same id is accepted as already received (WE-09)
        UUID weightId,
        UUID attendanceId,
        String employeeCode,
        // Phone time at save
        Instant weighedAt,
        BigDecimal weightKg,
        EntryType entryType,
        // What the phone applied; informational until CL-02 is decided
        BigDecimal variancePercent,
        // The phone's guess; the server assigns the final number (WE-10)
        Integer provisionalSequence
) {
    public RecordWeightRequest asManual() {
        return new RecordWeightRequest(weightId, attendanceId, employeeCode, weighedAt, weightKg,
                EntryType.MANUAL, variancePercent, provisionalSequence);
    }
}
