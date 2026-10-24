package com.atrum.agrum.weight;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.weight.dto.DailyWeightSummaryResultDto;
import com.atrum.agrum.weight.dto.DailyWeightSummarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * "Daily Weight Summary": read-only results of the 10 pm run (DS-03 = grant on these endpoints).
 * Pickers get the /mine endpoints instead of the full ones (DS-02).
 */
@ProjectionGroup("Daily Weight Summary")
@RestController
@RequestMapping("/api/daily-weight-summary")
@RequiredArgsConstructor
public class DailyWeightSummaryController {

    private final DailyWeightSummaryService dailyWeightSummaryService;
    private final DailyWeightSummaryJob dailyWeightSummaryJob;
    private final WeightExportService weightExportService;

    @Operation(summary = "Search the daily weight summary for all workers at the user's sites")
    @GetMapping
    public ResponseEntity<DailyWeightSummaryResultDto> searchDailyWeightSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType) {
        return ResponseEntity.ok(dailyWeightSummaryService.search(new DailyWeightSummarySearchRequest(
                dateFrom, dateTo, employeeCode, estateId, cpCode, workType)));
    }

    @Operation(summary = "Daily weight summary for pickers: only their own rows")
    @GetMapping("/mine")
    public ResponseEntity<DailyWeightSummaryResultDto> searchMyDailyWeightSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType) {
        return ResponseEntity.ok(dailyWeightSummaryService.searchMine(new DailyWeightSummarySearchRequest(
                dateFrom, dateTo, null, estateId, cpCode, workType)));
    }

    /** 9.2: every row matching the search (DS-04 when none). */
    @Operation(summary = "Export the daily weight summary to Excel")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDailyWeightSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType) {
        return weightExportService.dailySummary(dailyWeightSummaryService.search(new DailyWeightSummarySearchRequest(
                dateFrom, dateTo, employeeCode, estateId, cpCode, workType)).rows()).toResponse();
    }

    /** Pickers: their own rows only (TC-85). */
    @Operation(summary = "Export a picker's own daily summary to Excel")
    @GetMapping("/mine/export")
    public ResponseEntity<byte[]> exportMyDailyWeightSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType) {
        return weightExportService.dailySummary(dailyWeightSummaryService.searchMine(new DailyWeightSummarySearchRequest(
                dateFrom, dateTo, null, estateId, cpCode, workType)).rows()).toResponse();
    }

    /**
     * Admins: run the 10 pm recalculation now (today plus every date changed since the last run).
     * Not in the FSD; useful after setup changes and for testing. Returns the dates recalculated.
     */
    @Operation(summary = "Recalculate the daily summary now instead of waiting for 10 pm (administrators)")
    @PostMapping("/recalculate")
    public ResponseEntity<List<LocalDate>> recalculateDailyWeightSummary() {
        return ResponseEntity.ok(dailyWeightSummaryJob.run());
    }
}
