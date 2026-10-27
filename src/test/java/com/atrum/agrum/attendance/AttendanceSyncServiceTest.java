package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.dto.SyncAttendanceRecord;
import com.atrum.agrum.attendance.dto.SyncAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceResult;
import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.exception.InsufficientPermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Phone sync: every record gets its own Sent or Rejected answer, in the order sent (FSD 10.5, TC-38, AT-12).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSyncServiceTest {

    @Mock
    private AttendanceService attendanceService;
    @Mock
    private AttendanceRepository attendanceRepository;

    private AttendanceSyncService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceSyncService(attendanceService, attendanceRepository);
        when(attendanceService.recordSynced(any()))
                .thenAnswer(call -> SyncAttendanceResult.sent(call.<SyncAttendanceRecord>getArgument(0).attendanceId()));
    }

    @Test
    void TC38_aRejectedRecordKeepsItsReasonAndTheOthersAreStillSaved() {
        SyncAttendanceRecord first = record("E001");
        SyncAttendanceRecord duplicate = record("E002");
        SyncAttendanceRecord third = record("E003");
        String reason = "Attendance has already been recorded today for Worker E002 (E002).";
        when(attendanceService.recordSynced(duplicate)).thenThrow(new IllegalArgumentException(reason));

        List<SyncAttendanceResult> results = service.syncAttendance(new SyncAttendanceRequest(List.of(first, duplicate, third)));

        assertThat(results).extracting(SyncAttendanceResult::attendanceId)
                .containsExactly(first.attendanceId(), duplicate.attendanceId(), third.attendanceId());
        assertThat(results).extracting(SyncAttendanceResult::status)
                .containsExactly(SyncStatus.SENT, SyncStatus.REJECTED, SyncStatus.SENT);
        assertThat(results.get(1).reason()).isEqualTo(reason);
        var order = inOrder(attendanceService);
        order.verify(attendanceService).recordSynced(first);
        order.verify(attendanceService).recordSynced(duplicate);
        order.verify(attendanceService).recordSynced(third);
    }

    @Test
    void AT06_notPermittedForAnotherSite_isRejectedWithTheMessage() {
        SyncAttendanceRecord lent = record("E005");
        String reason = "You are not permitted to record Worker E005 (E005) at a different site.";
        when(attendanceService.recordSynced(lent)).thenThrow(new InsufficientPermissionException(reason));

        SyncAttendanceResult result = service.syncAttendance(new SyncAttendanceRequest(List.of(lent))).getFirst();

        assertThat(result.status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(result.reason()).isEqualTo(reason);
    }

    @Test
    void TC58_AT12_theSameIdSentTwiceAtOnce_isSentNotRejected() {
        // The second copy fails on the primary key, but the first one was saved
        SyncAttendanceRecord record = record("E001");
        when(attendanceService.recordSynced(record)).thenThrow(new IllegalArgumentException("duplicate key"));
        when(attendanceRepository.existsById(record.attendanceId())).thenReturn(true);

        SyncAttendanceResult result = service.syncAttendance(new SyncAttendanceRequest(List.of(record))).getFirst();

        assertThat(result.status()).isEqualTo(SyncStatus.SENT);
        assertThat(result.reason()).isNull();
    }

    @Test
    void anUnexpectedErrorRejectsOnlyThatRecordWithAGeneralMessage() {
        SyncAttendanceRecord broken = record("E001");
        SyncAttendanceRecord fine = record("E002");
        when(attendanceService.recordSynced(broken)).thenThrow(new IllegalStateException("boom"));

        List<SyncAttendanceResult> results = service.syncAttendance(new SyncAttendanceRequest(List.of(broken, fine)));

        assertThat(results.get(0).status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(results.get(0).reason()).isEqualTo("An unexpected server error occurred.");
        assertThat(results.get(1).status()).isEqualTo(SyncStatus.SENT);
    }

    @Test
    void noRecords_givesNoResults() {
        assertThat(service.syncAttendance(new SyncAttendanceRequest(null))).isEmpty();
        assertThat(service.syncAttendance(new SyncAttendanceRequest(List.of()))).isEmpty();
    }

    private static SyncAttendanceRecord record(String employeeCode) {
        return new SyncAttendanceRecord(UUID.randomUUID(), employeeCode, "S01", "PLUCKING", "CP1",
                AttendanceCategory.REGULAR, LocalDate.of(2026, 10, 24), Instant.parse("2026-10-24T04:00:00Z"));
    }
}
