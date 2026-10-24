package com.atrum.agrum.weight;

import com.atrum.agrum.exception.InsufficientPermissionException;
import com.atrum.agrum.weight.dto.RecordWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Receives weighings saved on phones while offline.
 * Not @Transactional: each record is saved in its own transaction,
 * so one rejected record does not fail the others.
 */
@Service
@RequiredArgsConstructor
public class WeightSyncService {

    private static final Logger log = LoggerFactory.getLogger(WeightSyncService.class);

    private final WeighingService weighingService;
    private final WeightRecordRepository weightRecordRepository;

    /** Records are processed in the order the phone sends them (recorded order). */
    public List<SyncWeightResult> syncWeights(SyncWeightRequest request) {
        if (request.records() == null) {
            return List.of();
        }
        return request.records().stream()
                .map(this::syncRecord)
                .toList();
    }

    private SyncWeightResult syncRecord(RecordWeightRequest record) {
        try {
            return weighingService.recordSynced(record);
        } catch (IllegalArgumentException | InsufficientPermissionException e) {
            return alreadyReceived(record, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not save synced weighing {}", record.weightId(), e);
            return alreadyReceived(record, "An unexpected server error occurred.");
        }
    }

    // WE-09: the same id sent twice at once fails on the primary key, but it was received
    private SyncWeightResult alreadyReceived(RecordWeightRequest record, String reason) {
        if (record.weightId() != null) {
            Optional<WeightRecord> existing = weightRecordRepository.findById(record.weightId());
            if (existing.isPresent()) {
                return SyncWeightResult.sent(existing.get());
            }
        }
        return SyncWeightResult.rejected(record.weightId(), reason);
    }
}
