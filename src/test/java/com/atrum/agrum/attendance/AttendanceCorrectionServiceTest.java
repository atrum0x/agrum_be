package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.CorrectAttendanceRequest;
import com.atrum.agrum.attendance.dto.DeleteAttendanceRequest;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.WeightRecordRepository;
import com.atrum.agrum.workType.WorkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Correct / Delete by an administrator (TC-41, TC-42, AT-15, AT-18). The weighing rules (AT-16,
 * AT-17, the cascade) are in AttendanceCorrectionWeighingsTest. AT-14 and TC-45 are endpoint
 * grants, checked by the request filter before these methods run.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceCorrectionServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AttendanceCorrectionRepository attendanceCorrectionRepository;
    @Mock
    private AttendanceSiteValidator siteValidator;
    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private UserService userService;

    private AttendanceCorrectionService service;
    private Attendance attendance;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        service = new AttendanceCorrectionService(attendanceRepository, attendanceCorrectionRepository,
                siteValidator, weightRecordRepository, userService, JsonMapper.builder().build());

        Estate s01 = new Estate();
        s01.setId("S01");
        WorkType plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        plucking.setWeighingRequired(true);
        CollectionPoint cp1 = collectionPoint("CP1");
        when(siteValidator.resolve("S01", "PLUCKING", "CP1")).thenReturn(new AttendanceSite(s01, plucking, cp1));
        when(siteValidator.resolve("S01", "PLUCKING", "CP2"))
                .thenReturn(new AttendanceSite(s01, plucking, collectionPoint("CP2")));

        Employee employee = new Employee();
        employee.setEmployeeCode("E001");
        AppUser recorder = new AppUser();
        recorder.setUsername("SUP01");
        attendance = new Attendance();
        attendance.setAttendanceId(UUID.randomUUID());
        attendance.setAttendanceDate(LocalDate.of(2026, 10, 24));
        attendance.setEmployee(employee);
        attendance.setEstateId("S01");
        attendance.setEstate(s01);
        attendance.setWorkType(plucking);
        attendance.setCollectionPoint(cp1);
        attendance.setAttendanceCategory(AttendanceCategory.REGULAR);
        attendance.setRecordedBy(recorder);
        attendance.setRowVersion(3);

        admin = new AppUser();
        admin.setUsername("ADMIN1");
        when(userService.getCurrentUser()).thenReturn(admin);
        when(attendanceRepository.findVisibleById(attendance.getAttendanceId())).thenReturn(Optional.of(attendance));
        when(attendanceCorrectionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void TC41_changingTheCollectionPointWritesAHistoryRowWithOldAndNewValues() {
        AttendanceDto dto = service.correctAttendance(attendance.getAttendanceId(),
                request("CP2", AttendanceCategory.REGULAR, "  Worker moved to the south shed  ", 3));

        assertThat(attendance.getCollectionPoint().getCpCode()).isEqualTo("CP2");
        verify(attendanceRepository).saveAndFlush(attendance);
        verify(weightRecordRepository).copyFromAttendance(attendance.getAttendanceId(), "S01", "CP2", "PLUCKING");

        AttendanceCorrection history = savedCorrection();
        assertThat(history.getAction()).isEqualTo(CorrectionAction.CORRECT);
        assertThat(history.getAttendanceId()).isEqualTo(attendance.getAttendanceId());
        assertThat(history.getEstateId()).isEqualTo("S01");
        assertThat(history.getReason()).isEqualTo("Worker moved to the south shed");
        assertThat(history.getCorrectedBy()).isSameAs(admin);
        assertThat(history.getOldValues()).contains("\"cpCode\":\"CP1\"");
        assertThat(history.getNewValues()).contains("\"cpCode\":\"CP2\"");
        // "Last Corrected By" in the list
        assertThat(dto).isNotNull();
    }

    @Test
    void TC42_AT15_correctionWithoutAReason_isRefused() {
        for (String reason : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(),
                    request("CP2", AttendanceCategory.REGULAR, reason, 3)))
                    .hasMessage(AttendanceMessages.AT_15_REASON);
        }
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void AT15_reasonOver300Characters_isRefused() {
        assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(),
                request("CP2", AttendanceCategory.REGULAR, "x".repeat(301), 3)))
                .hasMessage("Reason must be at most 300 characters");
        service.correctAttendance(attendance.getAttendanceId(),
                request("CP2", AttendanceCategory.REGULAR, "x".repeat(300), 3));
    }

    @Test
    void AT15_deleteWithoutAReason_isRefused() {
        assertThatThrownBy(() -> service.deleteAttendance(attendance.getAttendanceId(), new DeleteAttendanceRequest(" ", 3)))
                .hasMessage(AttendanceMessages.AT_15_REASON);
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void AT18_recordChangedSinceItWasOpened_isRefused() {
        assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(),
                request("CP2", AttendanceCategory.REGULAR, "Moved", 2)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> service.deleteAttendance(attendance.getAttendanceId(), new DeleteAttendanceRequest("Wrong", null)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(attendanceRepository, never()).saveAndFlush(any());
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void AT08_correctionWithoutACategory_isRefused() {
        assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(), request("CP2", null, "Moved", 3)))
                .hasMessage(AttendanceMessages.AT_08_CATEGORY);
    }

    @Test
    void aCorrectionThatChangesNothing_isRefused() {
        assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(),
                request("CP1", AttendanceCategory.REGULAR, "No change", 3)))
                .hasMessage("Nothing was changed.");
        verify(attendanceCorrectionRepository, never()).save(any());
    }

    @Test
    void theCategoryAloneCanBeCorrected() {
        service.correctAttendance(attendance.getAttendanceId(), request("CP1", AttendanceCategory.SEASONAL, "Seasonal", 3));

        assertThat(attendance.getAttendanceCategory()).isEqualTo(AttendanceCategory.SEASONAL);
        assertThat(savedCorrection().getNewValues()).contains("SEASONAL");
    }

    @Test
    void deleteKeepsAHistoryRowWithTheDeletedValues() {
        service.deleteAttendance(attendance.getAttendanceId(), new DeleteAttendanceRequest("Wrong worker ticked", 3));

        verify(attendanceRepository).delete(attendance);
        AttendanceCorrection history = savedCorrection();
        assertThat(history.getAction()).isEqualTo(CorrectionAction.DELETE);
        assertThat(history.getAttendanceId()).isEqualTo(attendance.getAttendanceId());
        assertThat(history.getOldValues()).contains("E001");
        assertThat(history.getNewValues()).isNull();
    }

    @Test
    void anAttendanceAtASiteTheUserCannotSee_isNotFound() {
        UUID hidden = UUID.randomUUID();
        when(attendanceRepository.findVisibleById(hidden)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correctAttendance(hidden, request("CP2", AttendanceCategory.REGULAR, "x", 0)))
                .hasMessage("Attendance not found: " + hidden);
        assertThatThrownBy(() -> service.deleteAttendance(hidden, new DeleteAttendanceRequest("x", 0)))
                .hasMessage("Attendance not found: " + hidden);
    }

    private AttendanceCorrection savedCorrection() {
        ArgumentCaptor<AttendanceCorrection> captor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(attendanceCorrectionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CorrectAttendanceRequest request(String cpCode, AttendanceCategory category, String reason, Integer version) {
        return new CorrectAttendanceRequest("S01", "PLUCKING", cpCode, category, reason, version);
    }

    private static CollectionPoint collectionPoint(String code) {
        CollectionPoint cp = new CollectionPoint();
        cp.setCpCode(code);
        return cp;
    }
}
