package com.atrum.agrum.attendance;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import com.atrum.agrum.attendance.dto.AttendanceDefaultsDto;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.AttendanceSearchRequest;
import com.atrum.agrum.attendance.dto.AttendanceWorkerDto;
import com.atrum.agrum.attendance.dto.RecordAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Recording and viewing attendance ("Attendance Management").
 * Correct / Delete are in AttendanceCorrectionController so they can be granted separately.
 */
@ProjectionGroup("Attendance Management")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    public static final String OTHER_SITE_PATH = "/api/attendance/other-site";

    private final AttendanceService attendanceService;
    private final AttendanceSyncService attendanceSyncService;

    @Operation(summary = "Attendance screen setup: today's date and the user's default site. Needed to record attendance")
    @GetMapping("/defaults")
    public ResponseEntity<AttendanceDefaultsDto> getAttendanceDefaults() {
        return ResponseEntity.ok(attendanceService.getDefaults());
    }

    @Operation(summary = "List workers who can be marked present today. Needed to record attendance")
    @GetMapping("/workers")
    public ResponseEntity<List<AttendanceWorkerDto>> getAvailableWorkers(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(attendanceService.getAvailableWorkers(search));
    }

    @Operation(summary = "Search attendance records (web list). Needed to open Attendance Management")
    @GetMapping
    public ResponseEntity<List<AttendanceDto>> searchAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String employee,
            @RequestParam(required = false) String estateId,
            @RequestParam(required = false) String cpCode,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) String recordedBy) {
        return ResponseEntity.ok(attendanceService.searchAttendance(new AttendanceSearchRequest(
                dateFrom, dateTo, employee, estateId, cpCode, workType, recordedBy)));
    }

    /** Web: one batch, saved all-or-nothing. */
    @Operation(summary = "Record attendance on the web for workers at their default site")
    @PostMapping
    public ResponseEntity<List<AttendanceDto>> recordAttendance(@RequestBody RecordAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.recordAttendance(request));
    }

    /**
     * Web: a batch that includes workers whose default site is not the working site (AT-06).
     * Granting this endpoint is what permits that, here and through /sync (CL-03).
     */
    @Operation(summary = "Record attendance for workers at a site other than their default site (lent / borrowed workers, AT-06). Also lets the phone send such records")
    @PostMapping("/other-site")
    public ResponseEntity<List<AttendanceDto>> recordOtherSiteAttendance(@RequestBody RecordAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.recordAttendance(request));
    }

    /** Phone: records saved offline; each one is Sent or Rejected on its own. */
    @Operation(summary = "Send attendance saved offline on the phone. Give to field supervisors")
    @PostMapping("/sync")
    public ResponseEntity<List<SyncAttendanceResult>> syncAttendance(@RequestBody SyncAttendanceRequest request) {
        return ResponseEntity.ok(attendanceSyncService.syncAttendance(request));
    }
}
