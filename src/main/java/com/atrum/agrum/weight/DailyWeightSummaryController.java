package com.atrum.agrum.weight;

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
@RestController
@RequestMapping("/api/daily-weight-summary")
@RequiredArgsConstructor
public class DailyWeightSummaryController {

    private final DailyWeightSummaryService dailyWeightSummaryService;
    private final DailyWeightSummaryJob dailyWeightSummaryJob;
    private final WeightExportService weightExportService;

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
    @PostMapping("/recalculate")
    public ResponseEntity<List<LocalDate>> recalculateDailyWeightSummary() {
        return ResponseEntity.ok(dailyWeightSummaryJob.run());
    }
}
