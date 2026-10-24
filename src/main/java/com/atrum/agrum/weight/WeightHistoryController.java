package com.atrum.agrum.weight;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.weight.dto.WeightAuditDto;
import com.atrum.agrum.weight.dto.WeightHistoryRowDto;
import com.atrum.agrum.weight.dto.WeightHistorySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "Weight History": search and View History. Requesting and approving changes are in
 * WeightChangeController and WeightApprovalController so they can be granted separately.
 */
@ProjectionGroup("Weight History Management")
@RestController
@RequestMapping("/api/weights")
@RequiredArgsConstructor
public class WeightHistoryController {

    private final WeightHistoryService weightHistoryService;
    private final WeightExportService weightExportService;

    @Operation(summary = "Search all weighings at the user's sites. Needed to open Weight History")
    @GetMapping
    public ResponseEntity<Page<WeightHistoryRowDto>> searchWeights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) AttendanceCategory attendanceCategory,
            @RequestParam(required = false) EntryType entryType,
            @RequestParam(required = false) LineState lineState,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(weightHistoryService.search(new WeightHistorySearchRequest(
                dateFrom, dateTo, employeeCode, supervisor, estateId, cpCode, workType,
                attendanceCategory, entryType, lineState, approvalStatus), pageable));
    }

    /** Pickers: grant this instead of the search above, so they see only their own weighings. */
    @Operation(summary = "Weight history for pickers: only their own weighings")
    @GetMapping("/mine")
    public ResponseEntity<Page<WeightHistoryRowDto>> searchMyWeights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) AttendanceCategory attendanceCategory,
            @RequestParam(required = false) EntryType entryType,
            @RequestParam(required = false) LineState lineState,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(weightHistoryService.searchMine(new WeightHistorySearchRequest(
                dateFrom, dateTo, null, supervisor, estateId, cpCode, workType,
                attendanceCategory, entryType, lineState, approvalStatus), pageable));
    }

    /** 9.1: every row matching the search (DS-04 when none). */
    @Operation(summary = "Export weight history to Excel")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWeights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) AttendanceCategory attendanceCategory,
            @RequestParam(required = false) EntryType entryType,
            @RequestParam(required = false) LineState lineState,
            @RequestParam(required = false) ApprovalStatus approvalStatus) {
        return weightExportService.weightHistory(weightHistoryService.searchAll(new WeightHistorySearchRequest(
                dateFrom, dateTo, employeeCode, supervisor, estateId, cpCode, workType,
                attendanceCategory, entryType, lineState, approvalStatus))).toResponse();
    }

    /** Pickers: their own rows only (TC-85). */
    @Operation(summary = "Export a picker's own weighings to Excel")
    @GetMapping("/mine/export")
    public ResponseEntity<byte[]> exportMyWeights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) AttendanceCategory attendanceCategory,
            @RequestParam(required = false) EntryType entryType,
            @RequestParam(required = false) LineState lineState,
            @RequestParam(required = false) ApprovalStatus approvalStatus) {
        return weightExportService.weightHistory(weightHistoryService.searchAllMine(new WeightHistorySearchRequest(
                dateFrom, dateTo, null, supervisor, estateId, cpCode, workType,
                attendanceCategory, entryType, lineState, approvalStatus))).toResponse();
    }

    @Operation(summary = "View the change history of a weighing")
    @GetMapping("/{weightId}/history")
    public ResponseEntity<WeightAuditDto> getWeightHistory(@PathVariable UUID weightId) {
        return ResponseEntity.ok(weightHistoryService.getAudit(weightId));
    }
}
