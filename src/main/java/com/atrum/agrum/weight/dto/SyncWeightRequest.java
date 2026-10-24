package com.atrum.agrum.weight.dto;

import java.util.List;

public record SyncWeightRequest(
        // In recorded order; the phone sends attendance first
        List<RecordWeightRequest> records
) {}
