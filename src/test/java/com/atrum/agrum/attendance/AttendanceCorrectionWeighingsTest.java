package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The attendance rules that depend on weighings: AT-16, AT-17 and copying a correction to the weighings.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceCorrectionWeighingsTest {

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
    private AttendanceSite plucking;
    private AttendanceSite pruning;
    private AttendanceSite pluckingAtS02;

    @BeforeEach
    void setUp() {
        service = new AttendanceCorrectionService(attendanceRepository, attendanceCorrectionRepository,
                siteValidator, weightRecordRepository, userService, JsonMapper.builder().build());

        Estate s01 = estate("S01");
        CollectionPoint cp1 = collectionPoint("CP1");
        plucking = new AttendanceSite(s01, workType("PLUCKING", true), cp1);
        pruning = new AttendanceSite(s01, workType("PRUNING", false), cp1);
        pluckingAtS02 = new AttendanceSite(estate("S02"), workType("PLUCKING2", true), collectionPoint("CP9"));

        Employee employee = new Employee();
        employee.setEmployeeCode("E001");
        AppUser recorder = new AppUser();
        recorder.setUsername("sup1");
        attendance = new Attendance();
        attendance.setAttendanceId(UUID.randomUUID());
        attendance.setAttendanceDate(LocalDate.of(2026, 10, 24));
        attendance.setEmployee(employee);
        attendance.setEstateId("S01");
        attendance.setEstate(s01);
        attendance.setWorkType(plucking.workType());
        attendance.setCollectionPoint(cp1);
        attendance.setAttendanceCategory(AttendanceCategory.REGULAR);
        attendance.setRecordedBy(recorder);
        attendance.setRowVersion(0);

        AppUser admin = new AppUser();
        admin.setUsername("admin");
        when(userService.getCurrentUser()).thenReturn(admin);
        when(attendanceRepository.findVisibleById(attendance.getAttendanceId())).thenReturn(Optional.of(attendance));
        when(attendanceCorrectionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(siteValidator.resolve("S01", "PLUCKING", "CP1")).thenReturn(plucking);
        when(siteValidator.resolve("S01", "PRUNING", "CP1")).thenReturn(pruning);
        when(siteValidator.resolve("S02", "PLUCKING2", "CP9")).thenReturn(pluckingAtS02);
    }

    @Test
    void AT16_changeToNonWeighingWorkTypeWithWeighings_isRejected() {
        when(weightRecordRepository.countActiveByAttendance(attendance.getAttendanceId())).thenReturn(1L);

        assertThatThrownBy(() -> service.correctAttendance(attendance.getAttendanceId(), correct("S01", "PRUNING", "CP1")))
                .hasMessage(AttendanceMessages.AT_16_WEIGHINGS_EXIST);
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void AT16_changeToNonWeighingWorkTypeWhenAllWeighingsCancelled_isAllowed() {
        when(weightRecordRepository.countActiveByAttendance(attendance.getAttendanceId())).thenReturn(0L);

        service.correctAttendance(attendance.getAttendanceId(), correct("S01", "PRUNING", "CP1"));

        verify(weightRecordRepository).copyFromAttendance(attendance.getAttendanceId(), "S01", "CP1", "PRUNING");
    }

    @Test
    void correction_copiesTheNewSiteCollectionPointAndWorkTypeToTheWeighings() {
        when(weightRecordRepository.countActiveByAttendance(attendance.getAttendanceId())).thenReturn(2L);

        service.correctAttendance(attendance.getAttendanceId(), correct("S02", "PLUCKING2", "CP9"));

        verify(weightRecordRepository).copyFromAttendance(attendance.getAttendanceId(), "S02", "CP9", "PLUCKING2");
    }

    @Test
    void AT17_deleteWithWeighings_isRejected() {
        when(weightRecordRepository.countByAttendance(attendance.getAttendanceId())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteAttendance(attendance.getAttendanceId(),
                new DeleteAttendanceRequest("Wrong worker", 0)))
                .hasMessage(AttendanceMessages.AT_17_CANNOT_DELETE);
        verify(attendanceRepository, never()).delete(any());
    }

    @Test
    void AT17_deleteWithoutWeighings_isAllowed() {
        when(weightRecordRepository.countByAttendance(attendance.getAttendanceId())).thenReturn(0L);

        service.deleteAttendance(attendance.getAttendanceId(), new DeleteAttendanceRequest("Wrong worker", 0));

        verify(attendanceRepository).delete(attendance);
        verify(weightRecordRepository, never()).copyFromAttendance(any(), anyString(), anyString(), anyString());
    }

    private static CorrectAttendanceRequest correct(String estateId, String workType, String cpCode) {
        return new CorrectAttendanceRequest(estateId, workType, cpCode, AttendanceCategory.REGULAR, "Wrong site", 0);
    }

    private static Estate estate(String id) {
        Estate estate = new Estate();
        estate.setId(id);
        estate.setActive(true);
        return estate;
    }

    private static WorkType workType(String code, boolean weighingRequired) {
        WorkType workType = new WorkType();
        workType.setWorkType(code);
        workType.setWeighingRequired(weighingRequired);
        return workType;
    }

    private static CollectionPoint collectionPoint(String code) {
        CollectionPoint cp = new CollectionPoint();
        cp.setCpCode(code);
        return cp;
    }
}
