package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.dto.AttendanceCorrectionDto;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.CorrectAttendanceRequest;
import com.atrum.agrum.attendance.dto.DeleteAttendanceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * "Correct Attendance": grant these endpoints only to administrators (AT-14).
 */
@RestController
@RequestMapping("/api/attendance/{attendanceId}")
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService attendanceCorrectionService;

    @PutMapping("/correction")
    public ResponseEntity<AttendanceDto> correctAttendance(
            @PathVariable UUID attendanceId,
            @RequestBody CorrectAttendanceRequest request) {
        return ResponseEntity.ok(attendanceCorrectionService.correctAttendance(attendanceId, request));
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteAttendance(
            @PathVariable UUID attendanceId,
            @RequestBody DeleteAttendanceRequest request) {
        attendanceCorrectionService.deleteAttendance(attendanceId, request);
        return ResponseEntity.ok("Attendance deleted");
    }

    @GetMapping("/corrections")
    public ResponseEntity<List<AttendanceCorrectionDto>> getAttendanceHistory(@PathVariable UUID attendanceId) {
        return ResponseEntity.ok(attendanceCorrectionService.getHistory(attendanceId));
    }
}
