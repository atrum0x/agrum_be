package com.atrum.agrum.attendance;

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
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceSyncService attendanceSyncService;

    @GetMapping("/defaults")
    public ResponseEntity<AttendanceDefaultsDto> getAttendanceDefaults() {
        return ResponseEntity.ok(attendanceService.getDefaults());
    }

    @GetMapping("/workers")
    public ResponseEntity<List<AttendanceWorkerDto>> getAvailableWorkers(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(attendanceService.getAvailableWorkers(search));
    }

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
    @PostMapping
    public ResponseEntity<List<AttendanceDto>> recordAttendance(@RequestBody RecordAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.recordAttendance(request));
    }

    /** Phone: records saved offline; each one is Sent or Rejected on its own. */
    @PostMapping("/sync")
    public ResponseEntity<List<SyncAttendanceResult>> syncAttendance(@RequestBody SyncAttendanceRequest request) {
        return ResponseEntity.ok(attendanceSyncService.syncAttendance(request));
    }
}
