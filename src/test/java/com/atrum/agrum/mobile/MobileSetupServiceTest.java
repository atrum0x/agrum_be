package com.atrum.agrum.mobile;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.attendance.AttendanceRepository;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.mobile.dto.MobileEstateDto;
import com.atrum.agrum.mobile.dto.MobileSetupDto;
import com.atrum.agrum.security.PermissionChecker;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.WeighingService;
import com.atrum.agrum.weight.dto.WeighableWorkerDto;
import com.atrum.agrum.workType.WorkType;
import com.atrum.agrum.workType.WorkTypeRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MobileSetupServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final Instant NOW = Instant.parse("2026-10-24T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private UserService userService;
    @Mock
    private PermissionChecker permissionChecker;
    @Mock
    private EstateRepository estateRepository;
    @Mock
    private WorkTypeRepository workTypeRepository;
    @Mock
    private CollectionPointRepository collectionPointRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private WeighingService weighingService;

    private MobileSetupService service;

    private Estate s01;
    private Estate s02;
    private Estate closed;
    private AppUser supervisor;

    @BeforeEach
    void setUp() {
        service = new MobileSetupService(userService, permissionChecker, estateRepository, workTypeRepository,
                collectionPointRepository, employeeRepository, attendanceRepository, weighingService,
                Clock.fixed(NOW, COLOMBO));

        s01 = estate("S01", true);
        s02 = estate("S02", true);
        closed = estate("S03", false);

        supervisor = new AppUser();
        supervisor.setUsername("SUP01");
        supervisor.getAllowedEstates().add(s01);
        supervisor.getAllowedEstates().add(closed);
        Employee self = new Employee();
        self.setEmployeeCode("SUP01");
        self.setDefaultEstate(s01);
        supervisor.setEmployee(self);
        when(userService.getCurrentUser()).thenReturn(supervisor);

        WorkType plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        plucking.setEstateId("S01");
        plucking.setWeighingRequired(true);
        when(workTypeRepository.findAllByEstate("S01")).thenReturn(List.of(plucking));

        CollectionPoint cp = new CollectionPoint();
        cp.setCpCode("CP1");
        cp.setEstateId("S01");
        cp.setName("North shed");
        cp.setDefault(true);
        when(collectionPointRepository.findAllByEstate("S01", "")).thenReturn(List.of(cp));

        Employee worker = new Employee();
        worker.setEmployeeCode("E001");
        worker.setFirstName("Kamala");
        worker.setLastName("Perera");
        worker.setDefaultEstate(s02);
        worker.setHomeEstate(s02);
        worker.setEmployeeCategory(AttendanceCategory.REGULAR);
        when(employeeRepository.findEmployedOn(EmploymentStatus.ACTIVE, TODAY, "")).thenReturn(List.of(worker));
        when(attendanceRepository.findAllEmployeeCodesRecordedOn(TODAY)).thenReturn(List.of("E002", "E002"));
        when(weighingService.getWeighableWorkers("")).thenReturn(List.of(new WeighableWorkerDto("E002", "Sunil Silva",
                UUID.fromString("6f1c2a3e-0000-4000-8000-000000000001"), "S01", "Site S01", "CP1", "North shed",
                "PLUCKING", new BigDecimal("2.00"), 1, 2, 1)));
    }

    @Test
    void supervisorGetsOnlyTheirActiveSitesButWorkersFromAllSites() {
        MobileSetupDto setup = service.getSetup();

        assertThat(setup.today()).isEqualTo(TODAY);
        assertThat(setup.serverTime()).isEqualTo(NOW);
        assertThat(setup.defaultEstateId()).isEqualTo("S01");
        assertThat(setup.estates()).extracting(MobileEstateDto::id).containsExactly("S01");
        assertThat(setup.workTypes()).singleElement().satisfies(wt -> {
            assertThat(wt.workType()).isEqualTo("PLUCKING");
            assertThat(wt.weighingRequired()).isTrue();
        });
        assertThat(setup.collectionPoints()).singleElement().satisfies(cp -> assertThat(cp.isDefault()).isTrue());
        assertThat(setup.workers()).singleElement().satisfies(w -> {
            assertThat(w.employeeCode()).isEqualTo("E001");
            assertThat(w.defaultEstateId()).isEqualTo("S02");
        });
        assertThat(setup.attendedToday()).containsExactly("E002");
        // The weighing screen's offline list (FSD 2.7, 5.4)
        assertThat(setup.weighableToday()).singleElement().satisfies(w -> {
            assertThat(w.employeeCode()).isEqualTo("E002");
            assertThat(w.entriesToday()).isEqualTo(1);
            assertThat(w.lastSequence()).isEqualTo(1);
        });
        verify(estateRepository, never()).findAll();
        verify(workTypeRepository, never()).findAllByEstate("S03");
    }

    @Test
    void superuserGetsEveryActiveSite() {
        when(permissionChecker.isSuperuser("SUP01")).thenReturn(true);
        when(estateRepository.findAll()).thenReturn(List.of(s02, closed, s01));

        assertThat(service.getSetup().estates()).extracting(MobileEstateDto::id).containsExactly("S01", "S02");
    }

    private static Estate estate(String id, boolean active) {
        Estate estate = new Estate();
        estate.setId(id);
        estate.setName("Site " + id);
        estate.setActive(active);
        estate.setVariancePercent(new BigDecimal("2.00"));
        estate.setMaxDailyTransactions(2);
        estate.setFullDayQtyKg(new BigDecimal("23.00"));
        estate.setHalfDayQtyKg(new BigDecimal("12.00"));
        return estate;
    }
}
