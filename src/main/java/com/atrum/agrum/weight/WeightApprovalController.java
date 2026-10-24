package com.atrum.agrum.weight;

import com.atrum.agrum.weight.dto.DecideWeightChangeRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Authorize Weight Lines": grant only to approvers (WH-08).
 */
@RestController
@RequestMapping("/api/weight-changes")
@RequiredArgsConstructor
public class WeightApprovalController {

    private final WeightApprovalService weightApprovalService;

    @GetMapping("/pending")
    public ResponseEntity<List<WeightChangeDto>> getPendingWeightChanges() {
        return ResponseEntity.ok(weightApprovalService.getPending());
    }

    @GetMapping("/pending/count")
    public ResponseEntity<Long> countPendingWeightChanges() {
        return ResponseEntity.ok(weightApprovalService.countPending());
    }

    @PostMapping("/{changeId}/approve")
    public ResponseEntity<WeightChangeDto> approveWeightChange(
            @PathVariable Long changeId,
            @RequestBody DecideWeightChangeRequest request) {
        return ResponseEntity.ok(weightApprovalService.approve(changeId, request));
    }

    @PostMapping("/{changeId}/disapprove")
    public ResponseEntity<WeightChangeDto> disapproveWeightChange(
            @PathVariable Long changeId,
            @RequestBody DecideWeightChangeRequest request) {
        return ResponseEntity.ok(weightApprovalService.disapprove(changeId, request));
    }
}
