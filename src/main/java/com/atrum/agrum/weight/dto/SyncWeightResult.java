package com.atrum.agrum.weight.dto;

import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.weight.WeightRecord;

import java.util.UUID;

public record SyncWeightResult(
        UUID weightId,
        SyncStatus status,
        // Final number from the server; the phone replaces its provisional one
        Integer entrySequence,
        // Shown on the phone next to a rejected record
        String reason
) {
    public static SyncWeightResult sent(WeightRecord record) {
        return new SyncWeightResult(record.getWeightId(), SyncStatus.SENT, record.getEntrySequence(), null);
    }

    public static SyncWeightResult rejected(UUID weightId, String reason) {
        return new SyncWeightResult(weightId, SyncStatus.REJECTED, null, reason);
    }
}
