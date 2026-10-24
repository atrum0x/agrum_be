package com.atrum.agrum.weight;

import com.atrum.agrum.weight.dto.RequestCancellationRequest;
import com.atrum.agrum.weight.dto.RequestCorrectionRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * "Correct Weight" / "Request Cancellation": grant only to users who may change weight records (WH-03).
 */
@RestController
@RequestMapping("/api/weights/{weightId}")
@RequiredArgsConstructor
public class WeightChangeController {

    private final WeightChangeService weightChangeService;

    @PostMapping("/correction")
    public ResponseEntity<WeightChangeDto> requestWeightCorrection(
            @PathVariable UUID weightId,
            @RequestBody RequestCorrectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weightChangeService.requestCorrection(weightId, request));
    }

    @PostMapping("/cancellation")
    public ResponseEntity<WeightChangeDto> requestWeightCancellation(
            @PathVariable UUID weightId,
            @RequestBody RequestCancellationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weightChangeService.requestCancellation(weightId, request));
    }
}
