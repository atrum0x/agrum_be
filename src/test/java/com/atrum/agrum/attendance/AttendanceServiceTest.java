package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.AttendanceSearchRequest;
import com.atrum.agrum.attendance.dto.AttendanceWorkerDto;
import com.atrum.agrum.attendance.dto.AttendanceWorkerRequest;
import com.atrum.agrum.attendance.dto.RecordAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceRecord;
import com.atrum.agrum.attendance.dto.SyncAttendanceResult;
import com.atrum.agrum.attendance.dto.SyncStatus;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.exception.InsufficientPermissionException;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.workType.WorkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recording attendance on the web and from phones (FSD 2.6, TC-31..TC-36, TC-38, TC-39, TC-58), with
 * the FSD section 11 test data: site S01, Plucking, collection point CP1, workers E001..E010 whose
 * default site is S01, and E005 lent from S02. Site, work type and collection point checks are in
 * AttendanceSiteValidatorTest; the estate filter needs a database and is not covered here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    // 10:00 in Colombo on 24/10/2026
    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AttendanceCorrectionRepository attendanceCorrectionRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceSiteValidator siteValidator;
    @Mock
    private UserService userService;
    @Mock
    private PermissionChecker permissionChecker;

    private AttendanceService service;
    private AppUser supervisor;
    private Estate s01;
    private final Map<String, Employee> employees = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, attendanceCorrectionRepository, employeeRepository,
                siteValidator, userService, permissionChecker, Clock.fixed(NOW, COLOMBO));

        s01 = estate("S01");
        Estate s02 = estate("S02");
        WorkType plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        plucking.setWeighingRequired(true);
        CollectionPoint cp1 = new CollectionPoint();
        cp1.setCpCode("CP1");
        when(siteValidator.resolve("S01", "PLUCKING", "CP1")).thenReturn(new AttendanceSite(s01, plucking, cp1));

        supervisor = new AppUser();
        supervisor.setUsername("SUP01");
        when(userService.getCurrentUser()).thenReturn(supervisor);

        for (int i = 1; i <= 10; i++) {
            worker(String.format("E%03d", i), s01);
        }
        worker("E005", s02); // Lent from S02
        worker("E099", null); // No default site

        // Like the real IN (...) query: each worker once, however often the code is asked for
        when(employeeRepository.findAllById(anyCollection())).thenAnswer(call -> {
            List<Employee> found = new ArrayList<>();
            for (Object code : new LinkedHashSet<>((Collection<?>) call.getArgument(0))) {
                Employee employee = employees.get(code);
                if (employee != null) {
                    found.add(employee);
                }
            }
            return found;
        });
        when(employeeRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(employees.get(call.getArgument(0))));
        when(attendanceRepository.findEmployeeCodesRecordedOn(any(), anyList())).thenReturn(List.of());
        when(attendanceRepository.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    // ---- Web batch -------------------------------------------------------------------------

    @Test
    void TC31_TC39_recordsOneRowPerWorkerForTodayWithTheBatchSiteWorkTypeAndCollectionPoint() {
        List<String> codes = List.of("E001", "E002", "E003", "E004", "E006", "E007", "E008", "E009", "E010", "E099");

        List<AttendanceDto> saved = service.recordAttendance(batch(codes.toArray(String[]::new)));

        assertThat(saved).hasSize(10);
        List<Attendance> rows = savedRows();
        assertThat(rows).extracting(row -> row.getEmployee().getEmployeeCode()).containsExactlyElementsOf(codes);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getAttendanceDate()).isEqualTo(TODAY);
            assertThat(row.getEstateId()).isEqualTo("S01");
            assertThat(row.getWorkType().getWorkType()).isEqualTo("PLUCKING");
            assertThat(row.getCollectionPoint().getCpCode()).isEqualTo("CP1");
            assertThat(row.getAttendanceCategory()).isEqualTo(AttendanceCategory.REGULAR);
            assertThat(row.getRecordedBy()).isSameAs(supervisor);
            assertThat(row.getRecordedAt()).isEqualTo(NOW);
            assertThat(row.getAttendanceId()).isNotNull();
        });
        assertThat(rows).extracting(Attendance::getAttendanceId).doesNotHaveDuplicates();
    }

    @Test
    void AT01_noWorkers_isRefused() {
        assertThatThrownBy(() -> service.recordAttendance(new RecordAttendanceRequest("S01", "PLUCKING", "CP1", List.of())))
                .hasMessage(AttendanceMessages.AT_01_NO_WORKERS);
        assertThatThrownBy(() -> service.recordAttendance(new RecordAttendanceRequest("S01", "PLUCKING", "CP1", null)))
                .hasMessage(AttendanceMessages.AT_01_NO_WORKERS);
    }

    @Test
    void TC34_unknownWorker_isRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E777")))
                .hasMessage("Worker not found: E777");
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void TC32_AT02_workerAlreadyRecordedToday_isRefused() {
        when(attendanceRepository.findEmployeeCodesRecordedOn(eq(TODAY), anyList())).thenReturn(List.of("E001"));

        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E002")))
                .hasMessage("Attendance has already been recorded today for Worker E001 (E001).");
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void AT02_sameWorkerTwiceInOneBatch_isRefused() {
        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E002", "E001")))
                .hasMessage("Attendance has already been recorded today for Worker E001 (E001).");
    }

    @Test
    void AT02_recordedByAnotherUserBetweenTheCheckAndTheSave_givesTheAT02Message() {
        when(attendanceRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("uk_attendance_employee_date"));

        assertThatThrownBy(() -> service.recordAttendance(batch("E001")))
                .hasMessage("Attendance has already been recorded today for Worker E001 (E001).");
        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E002")))
                .hasMessage("Attendance has already been recorded today for one of the selected workers.");
    }

    @Test
    void TC33_AT03_inactiveWorker_isRefused() {
        employees.get("E002").setEmploymentStatus(EmploymentStatus.INACTIVE);

        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E002")))
                .hasMessage("Worker E002 (E002): " + AttendanceMessages.AT_03_INACTIVE_WORKER);
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void AT04_workerNotYetStartedOrAlreadyLeft_isRefused() {
        employees.get("E001").setStartDate(TODAY.plusDays(1));
        employees.get("E002").setEndDate(TODAY.minusDays(1));

        assertThatThrownBy(() -> service.recordAttendance(batch("E001")))
                .hasMessage("Worker E001 (E001): " + AttendanceMessages.AT_04_NOT_EMPLOYED);
        assertThatThrownBy(() -> service.recordAttendance(batch("E002")))
                .hasMessage("Worker E002 (E002): " + AttendanceMessages.AT_04_NOT_EMPLOYED);
    }

    @Test
    void AT04_startingOrLeavingToday_isAllowed() {
        employees.get("E001").setStartDate(TODAY);
        employees.get("E002").setEndDate(TODAY);

        assertThat(service.recordAttendance(batch("E001", "E002"))).hasSize(2);
    }

    @Test
    void AT08_missingCategoryOnOneWorker_isRefused() {
        RecordAttendanceRequest request = new RecordAttendanceRequest("S01", "PLUCKING", "CP1", List.of(
                new AttendanceWorkerRequest("E001", AttendanceCategory.REGULAR),
                new AttendanceWorkerRequest("E002", null)));

        assertThatThrownBy(() -> service.recordAttendance(request))
                .hasMessage("Worker E002 (E002): " + AttendanceMessages.AT_08_CATEGORY);
    }

    @Test
    void TC35_AT06_lentWorkerFromAnotherSiteWithoutTheGrant_isRefused() {
        when(permissionChecker.isCurrentUserGranted("POST", AttendanceController.OTHER_SITE_PATH)).thenReturn(false);

        assertThatThrownBy(() -> service.recordAttendance(batch("E001", "E005")))
                .isInstanceOf(InsufficientPermissionException.class)
                .hasMessage("You are not permitted to record Worker E005 (E005) at a different site.");
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void AT06_lentWorkerWithTheOtherSiteGrant_isRecordedWithTheirHomeSite() {
        when(permissionChecker.isCurrentUserGranted("POST", AttendanceController.OTHER_SITE_PATH)).thenReturn(true);

        service.recordAttendance(batch("E005"));

        Attendance row = savedRows().getFirst();
        assertThat(row.getEstateId()).isEqualTo("S01");
        assertThat(row.getHomeEstate().getId()).isEqualTo("S02");
    }

    @Test
    void AT06_workerWithoutADefaultSite_needsNoGrant() {
        assertThat(service.recordAttendance(batch("E099"))).hasSize(1);
        verify(permissionChecker, never()).isCurrentUserGranted(any(), any());
    }

    // ---- Worker picker ---------------------------------------------------------------------

    @Test
    void TC32_pickerHidesWorkersAlreadyRecordedTodayOnAnySite() {
        when(attendanceRepository.findAllEmployeeCodesRecordedOn(TODAY)).thenReturn(List.of("E001", "E005"));
        // TC-33: the repository returns only Active workers employed today
        when(employeeRepository.findEmployedOn(EmploymentStatus.ACTIVE, TODAY, ""))
                .thenReturn(List.of(employees.get("E001"), employees.get("E002"), employees.get("E005")));

        List<AttendanceWorkerDto> workers = service.getAvailableWorkers("  ");

        assertThat(workers).extracting(AttendanceWorkerDto::employeeCode).containsExactly("E002");
        assertThat(workers.getFirst().defaultEstateId()).isEqualTo("S01");
        assertThat(workers.getFirst().defaultCategory()).isEqualTo(AttendanceCategory.REGULAR);
    }

    @Test
    void pickerSearchIsTrimmed() {
        service.getAvailableWorkers("  E00 ");
        verify(employeeRepository).findEmployedOn(EmploymentStatus.ACTIVE, TODAY, "E00");
    }

    // ---- Phone sync ------------------------------------------------------------------------

    @Test
    void phoneRecordForToday_isSavedWithThePhonesIdAndTime() {
        SyncAttendanceRecord record = phone("E001", TODAY, NOW.minusSeconds(3600));

        SyncAttendanceResult result = service.recordSynced(record);

        assertThat(result.status()).isEqualTo(SyncStatus.SENT);
        Attendance row = savedRows().getFirst();
        assertThat(row.getAttendanceId()).isEqualTo(record.attendanceId());
        assertThat(row.getRecordedAt()).isEqualTo(record.recordedAt());
        assertThat(row.getAttendanceDate()).isEqualTo(TODAY);
    }

    @Test
    void TC58_AT12_aRecordAlreadyReceived_isSentWithoutASecondRow() {
        SyncAttendanceRecord record = phone("E001", TODAY, NOW);
        when(attendanceRepository.existsById(record.attendanceId())).thenReturn(true);

        assertThat(service.recordSynced(record).status()).isEqualTo(SyncStatus.SENT);
        verify(attendanceRepository, never()).saveAllAndFlush(anyList());
        // Even a now-invalid record is not re-checked once it was received
        verify(siteValidator, never()).resolve(any(), any(), any());
    }

    @Test
    void TC36_AT11_phoneDateSetToYesterday_isRefused() {
        // The phone says yesterday but its own clock time is today
        assertThatThrownBy(() -> service.recordSynced(phone("E001", TODAY.minusDays(1), NOW)))
                .hasMessage(AttendanceMessages.AT_11_TODAY_ONLY);
    }

    @Test
    void AT11_recordedJustBeforeMidnightAndSentAfterIt_isAcceptedForItsDay() {
        // 23:59 on 23/10 in Colombo, sent at 10:00 on 24/10
        Instant recordedAt = LocalDate.of(2026, 10, 23).atTime(23, 59).atZone(COLOMBO).toInstant();

        SyncAttendanceResult result = service.recordSynced(phone("E001", TODAY.minusDays(1), recordedAt));

        assertThat(result.status()).isEqualTo(SyncStatus.SENT);
        assertThat(savedRows().getFirst().getAttendanceDate()).isEqualTo(TODAY.minusDays(1));
        verify(attendanceRepository).findEmployeeCodesRecordedOn(TODAY.minusDays(1), List.of("E001"));
    }

    @Test
    void AT11_futureDateOrTime_isRefused() {
        assertThatThrownBy(() -> service.recordSynced(phone("E001", TODAY.plusDays(1), NOW)))
                .hasMessage(AttendanceMessages.AT_11_TODAY_ONLY);
        // More than 5 minutes ahead of the server clock
        assertThatThrownBy(() -> service.recordSynced(phone("E001", TODAY, NOW.plusSeconds(6 * 60))))
                .hasMessage(AttendanceMessages.AT_11_TODAY_ONLY);
    }

    @Test
    void AT11_aPhoneClockSlightlyAhead_isAccepted() {
        assertThat(service.recordSynced(phone("E001", TODAY, NOW.plusSeconds(4 * 60))).status())
                .isEqualTo(SyncStatus.SENT);
    }

    @Test
    void AT11_missingDateOrTime_isRefused() {
        assertThatThrownBy(() -> service.recordSynced(phone("E001", null, NOW)))
                .hasMessage(AttendanceMessages.AT_11_TODAY_ONLY);
        assertThatThrownBy(() -> service.recordSynced(phone("E001", TODAY, null)))
                .hasMessage(AttendanceMessages.AT_11_TODAY_ONLY);
    }

    @Test
    void TC38_AT02_workerMarkedFromAnotherPhoneFirst_isRefused() {
        when(attendanceRepository.findEmployeeCodesRecordedOn(TODAY, List.of("E001"))).thenReturn(List.of("E001"));

        assertThatThrownBy(() -> service.recordSynced(phone("E001", TODAY, NOW)))
                .hasMessage("Attendance has already been recorded today for Worker E001 (E001).");
    }

    @Test
    void phoneRecordWithoutAnId_isRefused() {
        SyncAttendanceRecord record = new SyncAttendanceRecord(null, "E001", "S01", "PLUCKING", "CP1",
                AttendanceCategory.REGULAR, TODAY, NOW);

        assertThatThrownBy(() -> service.recordSynced(record)).hasMessage("Attendance ID is required");
    }

    @Test
    void phoneRecordForAnUnknownWorker_isRefused() {
        assertThatThrownBy(() -> service.recordSynced(phone(" E777 ", TODAY, NOW)))
                .hasMessage("Worker not found: E777");
    }

    // ---- Search ----------------------------------------------------------------------------

    @Test
    void searchDefaultsToTodayAndRefusesAReversedRange() {
        service.searchAttendance(new AttendanceSearchRequest(null, null, null, null, null, null, null));
        verify(attendanceRepository).search(TODAY, TODAY, "", "", "", "", "");

        assertThatThrownBy(() -> service.searchAttendance(
                new AttendanceSearchRequest(TODAY, TODAY.minusDays(1), null, null, null, null, null)))
                .hasMessage("Date From must be on or before Date To.");
    }

    // ---- Helpers ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Attendance> savedRows() {
        ArgumentCaptor<List<Attendance>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private static RecordAttendanceRequest batch(String... codes) {
        return new RecordAttendanceRequest("S01", "PLUCKING", "CP1", IntStream.range(0, codes.length)
                .mapToObj(i -> new AttendanceWorkerRequest(codes[i], AttendanceCategory.REGULAR))
                .toList());
    }

    private static SyncAttendanceRecord phone(String code, LocalDate date, Instant recordedAt) {
        return new SyncAttendanceRecord(UUID.randomUUID(), code, "S01", "PLUCKING", "CP1",
                AttendanceCategory.REGULAR, date, recordedAt);
    }

    private void worker(String code, Estate defaultEstate) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName("Worker");
        employee.setLastName(code);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setDefaultEstate(defaultEstate);
        employee.setHomeEstate(defaultEstate);
        employee.setEmployeeCategory(AttendanceCategory.REGULAR);
        employees.put(code, employee);
    }

    private static Estate estate(String id) {
        Estate estate = new Estate();
        estate.setId(id);
        estate.setName("Site " + id);
        estate.setActive(true);
        return estate;
    }
}
