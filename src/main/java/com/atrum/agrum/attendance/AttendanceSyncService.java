package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.dto.SyncAttendanceRecord;
import com.atrum.agrum.attendance.dto.SyncAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceResult;
import com.atrum.agrum.exception.InsufficientPermissionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Receives attendance saved on phones while offline.
 * Not @Transactional: each record is saved in its own transaction,
 * so one rejected record does not fail the others.
 */
@Service
@RequiredArgsConstructor
public class AttendanceSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSyncService.class);

    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;

    /** Records are processed in the order the phone sends them (recorded order). */
    public List<SyncAttendanceResult> syncAttendance(SyncAttendanceRequest request) {
        if (request.records() == null) {
            return List.of();
        }
        return request.records().stream()
                .map(this::syncRecord)
                .toList();
    }

    private SyncAttendanceResult syncRecord(SyncAttendanceRecord record) {
        try {
            return attendanceService.recordSynced(record);
        } catch (IllegalArgumentException | InsufficientPermissionException e) {
            return alreadyReceived(record)
                    ? SyncAttendanceResult.sent(record.attendanceId())
                    : SyncAttendanceResult.rejected(record.attendanceId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not save synced attendance {}", record.attendanceId(), e);
            return alreadyReceived(record)
                    ? SyncAttendanceResult.sent(record.attendanceId())
                    : SyncAttendanceResult.rejected(record.attendanceId(), "An unexpected server error occurred.");
        }
    }

    // AT-12: the same id sent twice at once fails on the primary key, but it was received
    private boolean alreadyReceived(SyncAttendanceRecord record) {
        return record.attendanceId() != null && attendanceRepository.existsById(record.attendanceId());
    }
}
