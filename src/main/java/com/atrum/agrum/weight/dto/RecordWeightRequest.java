package com.atrum.agrum.weight.dto;

import com.atrum.agrum.weight.EntryType;
import com.atrum.agrum.weight.IdentifiedBy;

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
        Integer provisionalSequence,
        // How the worker was picked; null from phones older than face recognition
        IdentifiedBy identifiedBy,
        // Face match score, 0-1, when picked by face
        BigDecimal faceSimilarity,
        // The recognised face as a small JPEG (Base64 in JSON), when picked by face
        byte[] photo
) {
    public RecordWeightRequest asManual() {
        return new RecordWeightRequest(weightId, attendanceId, employeeCode, weighedAt, weightKg,
                EntryType.MANUAL, variancePercent, provisionalSequence, identifiedBy, faceSimilarity, photo);
    }
}
