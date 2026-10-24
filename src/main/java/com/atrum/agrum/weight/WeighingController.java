package com.atrum.agrum.weight;

import com.atrum.agrum.weight.dto.RecordWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightResult;
import com.atrum.agrum.weight.dto.WeighableWorkerDto;
import com.atrum.agrum.weight.dto.WeighingDefaultsDto;
import com.atrum.agrum.weight.dto.WeightRecordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Recording weighings ("Enter Weight Management", WE-11).
 * Searching and changing weighings are in their own controllers so they can be granted separately.
 */
@RestController
@RequestMapping("/api/weights")
@RequiredArgsConstructor
public class WeighingController {

    private final WeighingService weighingService;
    private final WeightSyncService weightSyncService;

    @GetMapping("/defaults")
    public ResponseEntity<WeighingDefaultsDto> getWeighingDefaults() {
        return ResponseEntity.ok(weighingService.getDefaults());
    }

    @GetMapping("/workers")
    public ResponseEntity<List<WeighableWorkerDto>> getWeighableWorkers(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(weighingService.getWeighableWorkers(search));
    }

    /** Phone, online: one weighing. */
    @PostMapping
    public ResponseEntity<WeightRecordDto> recordWeight(@RequestBody RecordWeightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weighingService.recordWeight(request));
    }

    /** Phone: weighings saved offline; each one is Sent or Rejected on its own. */
    @PostMapping("/sync")
    public ResponseEntity<List<SyncWeightResult>> syncWeights(@RequestBody SyncWeightRequest request) {
        return ResponseEntity.ok(weightSyncService.syncWeights(request));
    }
}
