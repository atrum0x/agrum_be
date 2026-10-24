package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.Attendance;
import com.atrum.agrum.attendance.AttendanceRepository;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.exception.InsufficientPermissionException;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.RecordWeightRequest;
import com.atrum.agrum.weight.dto.WeightRecordDto;
import com.atrum.agrum.workType.WorkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test data from FSD section 11: site S01 (full 23, half 12, limit 2, variance +2%),
 * Plucking (weighing required) and Pruning (not), worker E001.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeighingServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    // 24/10/2026 10:00 in Colombo
    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private UserService userService;
    @Mock
    private PermissionChecker permissionChecker;

    private WeighingService weighingService;

    private Estate site;
    private WorkType plucking;
    private Attendance attendance;

    @BeforeEach
    void setUp() {
        weighingService = new WeighingService(weightRecordRepository, attendanceRepository, userService,
                permissionChecker, Clock.fixed(NOW, COLOMBO));

        site = new Estate();
        site.setId("S01");
        site.setActive(true);
        site.setFullDayQtyKg(new BigDecimal("23.00"));
        site.setHalfDayQtyKg(new BigDecimal("12.00"));
        site.setMaxDailyTransactions(2);
        site.setVariancePercent(new BigDecimal("2.00"));

        plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        plucking.setWeighingRequired(true);

        CollectionPoint cp = new CollectionPoint();
        cp.setCpCode("CP1");

        Employee employee = new Employee();
        employee.setEmployeeCode("E001");

        attendance = new Attendance();
        attendance.setAttendanceId(UUID.randomUUID());
        attendance.setAttendanceDate(TODAY);
        attendance.setEmployee(employee);
        attendance.setEstateId("S01");
        attendance.setEstate(site);
        attendance.setWorkType(plucking);
        attendance.setCollectionPoint(cp);

        AppUser supervisor = new AppUser();
        supervisor.setUsername("sup1");
        when(userService.getCurrentUser()).thenReturn(supervisor);
        when(weightRecordRepository.findById(any())).thenReturn(Optional.empty());
        when(attendanceRepository.findVisibleByIdForUpdate(attendance.getAttendanceId()))
                .thenReturn(Optional.of(attendance));
        when(weightRecordRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        weighings(0, 0);
    }

    @Test
    void TC51_capturedWeighing_isSavedWithSequence1AndVariance() {
        WeightRecordDto saved = weighingService.recordWeight(request("12.00", EntryType.AUTOMATIC));

        assertThat(saved.entrySequence()).isEqualTo(1);
        assertThat(saved.entryType()).isEqualTo(EntryType.AUTOMATIC);
        assertThat(saved.variancePercent()).isEqualTo("2.00");
        assertThat(saved.weightAfterVariance()).isEqualTo("12.24");
        assertThat(saved.originalWeightKg()).isEqualTo("12.00");
        assertThat(saved.weighDate()).isEqualTo(TODAY);
        assertThat(saved.supervisor()).isEqualTo("sup1");
        // Site, collection point and work type come from the attendance
        assertThat(saved.estateId()).isEqualTo("S01");
        assertThat(saved.cpCode()).isEqualTo("CP1");
        assertThat(saved.workType()).isEqualTo("PLUCKING");
        assertThat(saved.lineState()).isEqualTo(LineState.RECORDED);
        assertThat(saved.approvalStatus()).isEqualTo(ApprovalStatus.RECORDED);
    }

    @Test
    void TC52_thirdWeighingWithLimit2_isRejected() {
        weighings(2, 2);

        assertThatThrownBy(() -> weighingService.recordWeight(request("12.00", EntryType.AUTOMATIC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum of 2 weighings per day already recorded for this worker.");
        verify(weightRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void TC53_afterWeighing2IsCancelled_nextWeighingIsNumbered3() {
        weighings(1, 2);

        assertThat(weighingService.recordWeight(request("10.00", EntryType.AUTOMATIC)).entrySequence())
                .isEqualTo(3);
    }

    @Test
    void TC56_zeroNegativeOrThreeDecimalWeight_isRejected() {
        for (String weight : new String[]{"0", "-5.00", "12.345"}) {
            assertThatThrownBy(() -> weighingService.recordWeight(request(weight, EntryType.MANUAL)))
                    .hasMessage(WeightMessages.WE_06_WEIGHT);
        }
    }

    @Test
    void TC55_manualEntryWithoutTheManualGrant_isRefused() {
        when(permissionChecker.isCurrentUserGranted("POST", WeighingController.MANUAL_PATH)).thenReturn(false);

        assertThatThrownBy(() -> weighingService.recordWeight(request("12.00", EntryType.MANUAL)))
                .isInstanceOf(InsufficientPermissionException.class)
                .hasMessage(WeightMessages.WE_08_NO_MANUAL);
    }

    @Test
    void WE08_manualEntryWithTheManualGrant_isSaved() {
        when(permissionChecker.isCurrentUserGranted("POST", WeighingController.MANUAL_PATH)).thenReturn(true);

        assertThat(weighingService.recordWeight(request("12.00", EntryType.MANUAL)).entryType())
                .isEqualTo(EntryType.MANUAL);
    }

    @Test
    void TC58_sameWeighingSentTwice_returnsTheStoredRecord() {
        RecordWeightRequest request = request("12.00", EntryType.AUTOMATIC);
        WeightRecord stored = new WeightRecord();
        stored.setWeightId(request.weightId());
        stored.setEntrySequence(1);
        when(weightRecordRepository.findById(request.weightId())).thenReturn(Optional.of(stored));

        assertThat(weighingService.recordSynced(request).entrySequence()).isEqualTo(1);
        verify(weightRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void WE01_noWorker_isRejected() {
        RecordWeightRequest request = new RecordWeightRequest(UUID.randomUUID(), attendance.getAttendanceId(),
                " ", NOW, new BigDecimal("12.00"), EntryType.AUTOMATIC, null, null);

        assertThatThrownBy(() -> weighingService.recordWeight(request)).hasMessage(WeightMessages.WE_01_SELECT_WORKER);
    }

    @Test
    void TC47_WE02_attendanceOfAnotherWorkerOrInvisible_isRejected() {
        RecordWeightRequest otherWorker = new RecordWeightRequest(UUID.randomUUID(), attendance.getAttendanceId(),
                "E002", NOW, new BigDecimal("12.00"), EntryType.AUTOMATIC, null, null);
        RecordWeightRequest unknownAttendance = new RecordWeightRequest(UUID.randomUUID(), UUID.randomUUID(),
                "E001", NOW, new BigDecimal("12.00"), EntryType.AUTOMATIC, null, null);

        assertThatThrownBy(() -> weighingService.recordWeight(otherWorker)).hasMessage(WeightMessages.WE_02_NO_ATTENDANCE);
        assertThatThrownBy(() -> weighingService.recordWeight(unknownAttendance)).hasMessage(WeightMessages.WE_02_NO_ATTENDANCE);
    }

    @Test
    void TC46_WE03_pruningAttendance_isRejected() {
        plucking.setWeighingRequired(false);

        assertThatThrownBy(() -> weighingService.recordWeight(request("12.00", EntryType.AUTOMATIC)))
                .hasMessage(WeightMessages.WE_03_NOT_WEIGHING);
    }

    @Test
    void WE05_inactiveSite_isRejected() {
        site.setActive(false);

        assertThatThrownBy(() -> weighingService.recordWeight(request("12.00", EntryType.AUTOMATIC)))
                .hasMessage(WeightMessages.WE_05_INACTIVE_SITE);
    }

    @Test
    void D10_weighingTimedOnAnotherDay_isRejected() {
        RecordWeightRequest yesterday = new RecordWeightRequest(UUID.randomUUID(), attendance.getAttendanceId(),
                "E001", NOW.minusSeconds(86_400), new BigDecimal("12.00"), EntryType.AUTOMATIC, null, null);

        assertThatThrownBy(() -> weighingService.recordWeight(yesterday)).hasMessage(WeightMessages.TODAY_ONLY);
    }

    @Test
    void TC61_varianceIsSavedFromTheSiteAtWeighingTime() {
        WeightRecordDto saved = weighingService.recordWeight(request("25.00", EntryType.AUTOMATIC));
        site.setVariancePercent(new BigDecimal("5.00"));

        assertThat(saved.variancePercent()).isEqualTo("2.00");
        assertThat(saved.weightAfterVariance()).isEqualTo("25.50");
    }

    private void weighings(long active, int maxSequence) {
        when(weightRecordRepository.countActive("E001", TODAY)).thenReturn(active);
        when(weightRecordRepository.maxSequence("E001", TODAY)).thenReturn(maxSequence);
    }

    private RecordWeightRequest request(String weightKg, EntryType entryType) {
        return new RecordWeightRequest(UUID.randomUUID(), attendance.getAttendanceId(), "E001", NOW,
                new BigDecimal(weightKg), entryType, null, null);
    }
}
