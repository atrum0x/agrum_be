package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.weight.dto.RecordWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeightSyncServiceTest {

    @Mock
    private WeighingService weighingService;
    @Mock
    private WeightRecordRepository weightRecordRepository;
    @InjectMocks
    private WeightSyncService weightSyncService;

    @Test
    void TC54_rejectedWeighingHasAReason_andTheOthersAreStillSaved() {
        RecordWeightRequest third = record();
        RecordWeightRequest other = record();
        when(weighingService.recordSynced(third)).thenThrow(
                new IllegalArgumentException(WeightMessages.limitReached(2)));
        when(weightRecordRepository.findById(third.weightId())).thenReturn(Optional.empty());
        when(weighingService.recordSynced(other)).thenReturn(
                new SyncWeightResult(other.weightId(), SyncStatus.SENT, 1, null));

        List<SyncWeightResult> results = weightSyncService.syncWeights(new SyncWeightRequest(List.of(third, other)));

        assertThat(results.get(0).status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(results.get(0).reason()).isEqualTo("Maximum of 2 weighings per day already recorded for this worker.");
        assertThat(results.get(1).status()).isEqualTo(SyncStatus.SENT);
        assertThat(results.get(1).entrySequence()).isEqualTo(1);
    }

    @Test
    void TC58_failureForAnIdAlreadySaved_isReportedAsSent() {
        RecordWeightRequest record = record();
        WeightRecord stored = new WeightRecord();
        stored.setWeightId(record.weightId());
        stored.setEntrySequence(2);
        when(weighingService.recordSynced(record)).thenThrow(new RuntimeException("duplicate key"));
        when(weightRecordRepository.findById(record.weightId())).thenReturn(Optional.of(stored));

        SyncWeightResult result = weightSyncService.syncWeights(new SyncWeightRequest(List.of(record))).getFirst();

        assertThat(result.status()).isEqualTo(SyncStatus.SENT);
        assertThat(result.entrySequence()).isEqualTo(2);
    }

    private static RecordWeightRequest record() {
        return new RecordWeightRequest(UUID.randomUUID(), UUID.randomUUID(), "E001", Instant.now(),
                new BigDecimal("12.00"), EntryType.AUTOMATIC, null, null);
    }
}
