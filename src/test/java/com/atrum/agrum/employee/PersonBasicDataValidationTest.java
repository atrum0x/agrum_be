package com.atrum.agrum.employee;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.auth.AuthService;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.employee.dto.CreateEmployeeRequest;
import com.atrum.agrum.employee.dto.UpdateEmployeeRequest;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.estate.mapper.EstateMapper;
import com.atrum.agrum.permission.PermissionSetRepository;
import com.atrum.agrum.permission.mapper.PermissionSetMapper;
import com.atrum.agrum.security.PermissionCacheEvictor;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Person Basic Data checks PB-01 to PB-08 (FSD 2.4). PB-09, PB-10 and PB-13 are site grants and
 * permission sets; PB-11 (default collection point in the default site) was already checked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonBasicDataValidationTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EstateRepository estateRepository;
    @Mock
    private CollectionPointRepository collectionPointRepository;
    @Mock
    private PermissionSetRepository permissionSetRepository;
    @Mock
    private EstateMapper estateMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PermissionSetMapper permissionSetMapper;
    @Mock
    private UserService userService;
    @Mock
    private PermissionCacheEvictor permissionCacheEvictor;
    @Mock
    private AuthService authService;

    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employeeRepository, estateRepository, collectionPointRepository,
                permissionSetRepository, estateMapper, employeeMapper, permissionSetMapper, userService,
                permissionCacheEvictor, authService);
        Estate s01 = new Estate();
        s01.setId("S01");
        when(estateRepository.findById("S01")).thenReturn(Optional.of(s01));
        when(employeeRepository.findFirstByNicIgnoreCaseAndEmployeeCodeNot(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(employeeMapper.fromCreateRequest(any())).thenAnswer(call -> new Employee());
        when(employeeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aValidPersonIsSavedWithTheCodeAndNicTrimmedAndInCapitals() {
        service.create(person(" e001 ", "Kamala", "Perera", " 861234567v ", AttendanceCategory.REGULAR, START, null, "S01"));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());
        assertThat(saved.getValue().getEmployeeCode()).isEqualTo("E001");
        assertThat(saved.getValue().getNic()).isEqualTo("861234567V");
        assertThat(saved.getValue().getHomeEstate().getId()).isEqualTo("S01");
    }

    @Test
    void PB01_employeeNoMandatoryAndUnique() {
        for (String code : new String[]{null, " ", "E".repeat(31)}) {
            assertThatThrownBy(() -> service.create(valid(code)))
                    .hasMessage(PersonMessages.PB_01_EMPLOYEE_NO);
        }
        when(employeeRepository.existsById("E001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(valid("E001")))
                .hasMessage(PersonMessages.PB_01_EMPLOYEE_NO_EXISTS);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void PB02_nameMandatory() {
        assertThatThrownBy(() -> service.create(person("E001", " ", "Perera", "861234567V", AttendanceCategory.REGULAR, START, null, "S01")))
                .hasMessage(PersonMessages.PB_02_NAME);
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", null, "861234567V", AttendanceCategory.REGULAR, START, null, "S01")))
                .hasMessage(PersonMessages.PB_02_NAME);
    }

    @Test
    void PB03_PB04_nicMandatoryAndUniqueAcrossPersons() {
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "", AttendanceCategory.REGULAR, START, null, "S01")))
                .hasMessage(PersonMessages.PB_03_NIC);

        Employee other = new Employee();
        other.setEmployeeCode("E009");
        when(employeeRepository.findFirstByNicIgnoreCaseAndEmployeeCodeNot("861234567V", "E001"))
                .thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "861234567v", AttendanceCategory.REGULAR, START, null, "S01")))
                .hasMessage("A person with this NIC already exists (Employee No: E009).");
    }

    @Test
    void PB04_keepingYourOwnNicOnUpdateIsAllowed() {
        Employee existing = new Employee();
        existing.setEmployeeCode("E001");
        existing.setNic("861234567V");
        when(employeeRepository.findById("E001")).thenReturn(Optional.of(existing));

        service.update("E001", new UpdateEmployeeRequest("Kamala", "Perera", "861234567V", null, null, null,
                AttendanceCategory.REGULAR, EmploymentStatus.ACTIVE, START, null, "S01", null, "S01"));

        verify(employeeRepository).findFirstByNicIgnoreCaseAndEmployeeCodeNot("861234567V", "E001");
        verify(employeeRepository).save(existing);
    }

    @Test
    void PB05_categoryMandatory() {
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "861234567V", null, START, null, "S01")))
                .hasMessage(PersonMessages.PB_05_CATEGORY);
    }

    @Test
    void PB06_PB07_startDateMandatoryAndEndDateNotBeforeIt() {
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "861234567V", AttendanceCategory.REGULAR, null, null, "S01")))
                .hasMessage(PersonMessages.PB_06_START_DATE);
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "861234567V", AttendanceCategory.REGULAR, START, START.minusDays(1), "S01")))
                .hasMessage(PersonMessages.PB_07_END_DATE);
        // Same day is allowed
        service.create(person("E001", "Kamala", "Perera", "861234567V", AttendanceCategory.REGULAR, START, START, "S01"));
    }

    @Test
    void PB08_homeSiteMandatory() {
        assertThatThrownBy(() -> service.create(person("E001", "Kamala", "Perera", "861234567V", AttendanceCategory.REGULAR, START, null, " ")))
                .hasMessage(PersonMessages.PB_08_HOME_SITE);
    }

    @Test
    void PB10_defaultSiteMandatory() {
        CreateEmployeeRequest noDefault = new CreateEmployeeRequest("E001", "Kamala", "Perera", "861234567V", null,
                null, null, AttendanceCategory.REGULAR, EmploymentStatus.ACTIVE, START, null, " ", null, "S01", false, null);

        assertThatThrownBy(() -> service.create(noDefault)).hasMessage(PersonMessages.PB_10_DEFAULT_SITE);
    }

    @Test
    void PB10_aNewLoginIsGrantedItsDefaultSite() {
        Employee saved = new Employee();
        when(employeeMapper.fromCreateRequest(any())).thenReturn(saved);
        AppUser login = new AppUser();
        login.setUsername("E001");
        when(userService.create(any())).thenAnswer(call -> {
            saved.setUserAccount(login);
            return null;
        });
        CreateEmployeeRequest withLogin = new CreateEmployeeRequest("E001", "Kamala", "Perera", "861234567V", null,
                null, null, AttendanceCategory.REGULAR, EmploymentStatus.ACTIVE, START, null, "S01", null, "S01", true, "pw");

        service.create(withLogin);

        assertThat(login.getAllowedEstates()).extracting(Estate::getId).containsExactly("S01");
    }

    @Test
    void PB10_aLoginsDefaultSiteMustBeOneOfItsGrantedSites() {
        Employee existing = new Employee();
        existing.setEmployeeCode("E001");
        AppUser login = new AppUser();
        login.setUsername("E001");
        existing.setUserAccount(login);
        when(employeeRepository.findById("E001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update("E001", new UpdateEmployeeRequest("Kamala", "Perera", "861234567V",
                null, null, null, AttendanceCategory.REGULAR, EmploymentStatus.ACTIVE, START, null, "S01", null, "S01")))
                .hasMessage(PersonMessages.PB_10_DEFAULT_SITE);
    }

    @Test
    void PB10_theDefaultSiteCannotBeRevoked() {
        Employee existing = new Employee();
        existing.setEmployeeCode("E001");
        existing.setDefaultEstate(estateRepository.findById("S01").orElseThrow());
        existing.setUserAccount(new AppUser());
        when(employeeRepository.findById("E001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.revokeEstates("E001", java.util.List.of("S01")))
                .hasMessage(PersonMessages.PB_10_DEFAULT_SITE);
    }

    private static CreateEmployeeRequest valid(String code) {
        return person(code, "Kamala", "Perera", "861234567V", AttendanceCategory.REGULAR, START, null, "S01");
    }

    private static CreateEmployeeRequest person(String code, String firstName, String lastName, String nic,
                                                AttendanceCategory category, LocalDate start, LocalDate end,
                                                String homeEstateId) {
        return new CreateEmployeeRequest(code, firstName, lastName, nic, null, null, null, category,
                EmploymentStatus.ACTIVE, start, end, "S01", null, homeEstateId, false, null);
    }
}
