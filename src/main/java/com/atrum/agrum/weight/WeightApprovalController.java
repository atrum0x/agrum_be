package com.atrum.agrum.weight;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.weight.dto.DecideWeightChangeRequest;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Authorize Weight Lines": grant only to approvers (WH-08).
 */
@ProjectionGroup("Weight History Management - Approve")
@RestController
@RequestMapping("/api/weight-changes")
@RequiredArgsConstructor
public class WeightApprovalController {

    private final WeightApprovalService weightApprovalService;

    @Operation(summary = "List weight changes waiting for approval. Needed to open Pending Approvals")
    @GetMapping("/pending")
    public ResponseEntity<List<WeightChangeDto>> getPendingWeightChanges() {
        return ResponseEntity.ok(weightApprovalService.getPending());
    }

    @Operation(summary = "Show the Pending Authorizations count on the dashboard (approvers)")
    @GetMapping("/pending/count")
    public ResponseEntity<Long> countPendingWeightChanges() {
        return ResponseEntity.ok(weightApprovalService.countPending());
    }

    @Operation(summary = "Approve a weight correction or cancellation (approvers)")
    @PostMapping("/{changeId}/approve")
    public ResponseEntity<WeightChangeDto> approveWeightChange(
            @PathVariable Long changeId,
            @RequestBody DecideWeightChangeRequest request) {
        return ResponseEntity.ok(weightApprovalService.approve(changeId, request));
    }

    @Operation(summary = "Reject a weight correction or cancellation (approvers)")
    @PostMapping("/{changeId}/disapprove")
    public ResponseEntity<WeightChangeDto> disapproveWeightChange(
            @PathVariable Long changeId,
            @RequestBody DecideWeightChangeRequest request) {
        return ResponseEntity.ok(weightApprovalService.disapprove(changeId, request));
    }
}
