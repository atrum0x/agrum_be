package com.atrum.agrum.weight;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
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
@ProjectionGroup("Enter Weight Management")
@RestController
@RequestMapping("/api/weights")
@RequiredArgsConstructor
public class WeighingController {

    // Granting this endpoint is what permits manual entry (WE-08, CL-03)
    public static final String MANUAL_PATH = "/api/weights/manual";

    private final WeighingService weighingService;
    private final WeightSyncService weightSyncService;

    @Operation(summary = "Weighing screen setup: today's date and server time. Needed to record weights")
    @GetMapping("/defaults")
    public ResponseEntity<WeighingDefaultsDto> getWeighingDefaults() {
        return ResponseEntity.ok(weighingService.getDefaults());
    }

    @Operation(summary = "List workers who can be weighed today. Needed to record weights")
    @GetMapping("/workers")
    public ResponseEntity<List<WeighableWorkerDto>> getWeighableWorkers(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(weighingService.getWeighableWorkers(search));
    }

    /** Phone, online: one weighing. */
    @Operation(summary = "Record a weight read from the scale (phone, online)")
    @PostMapping
    public ResponseEntity<WeightRecordDto> recordWeight(@RequestBody RecordWeightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weighingService.recordWeight(request));
    }

    /**
     * Phone, online: one weighing typed in because there is no scale reading (WE-08).
     * Only users granted this endpoint may enter weights manually, here or through /sync.
     */
    @Operation(summary = "Type in a weight by hand when there is no scale reading (WE-08). Also lets the phone send manual weighings")
    @PostMapping("/manual")
    public ResponseEntity<WeightRecordDto> recordManualWeight(@RequestBody RecordWeightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weighingService.recordWeight(request.asManual()));
    }

    /** Phone: weighings saved offline; each one is Sent or Rejected on its own. */
    @Operation(summary = "Send weighings saved offline on the phone. Give to field supervisors")
    @PostMapping("/sync")
    public ResponseEntity<List<SyncWeightResult>> syncWeights(@RequestBody SyncWeightRequest request) {
        return ResponseEntity.ok(weightSyncService.syncWeights(request));
    }
}
