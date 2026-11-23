package com.atrum.agrum.estate;

import com.atrum.agrum.division.Division;
import com.atrum.agrum.division.DivisionId;
import com.atrum.agrum.division.DivisionRepository;
import com.atrum.agrum.division.DivisionService;
import com.atrum.agrum.division.dto.CreateDivisionRequest;
import com.atrum.agrum.division.dto.UpdateDivisionRequest;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.dto.CreateEstateRequest;
import com.atrum.agrum.field.Field;
import com.atrum.agrum.field.FieldId;
import com.atrum.agrum.field.FieldRepository;
import com.atrum.agrum.field.FieldService;
import com.atrum.agrum.field.dto.CreateFieldRequest;
import com.atrum.agrum.field.dto.UpdateFieldRequest;
import com.atrum.agrum.security.PermissionCacheEvictor;
import com.atrum.agrum.user.AppUserRepository;
import com.atrum.agrum.workType.WorkType;
import com.atrum.agrum.workType.WorkTypeId;
import com.atrum.agrum.workType.WorkTypeRepository;
import com.atrum.agrum.workType.WorkTypeService;
import com.atrum.agrum.workType.dto.CreateWorkTypeRequest;
import com.atrum.agrum.workType.dto.UpdateWorkTypeRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Site Basic Data checks SB-01 to SB-17 (FSD v2.7 2.3, TC-97 to TC-100). SB-10 (one default field
 * per division) is kept by clearing the other defaults; SB-14 is the endpoint grant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SiteBasicDataValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private EstateRepository estateRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PermissionCacheEvictor permissionCacheEvictor;
    @Mock
    private WorkTypeRepository workTypeRepository;
    @Mock
    private DivisionRepository divisionRepository;
    @Mock
    private FieldRepository fieldRepository;

    private EstateService estates;
    private WorkTypeService workTypes;
    private DivisionService divisions;
    private FieldService fields;

    @BeforeEach
    void setUp() {
        estates = new EstateService(estateRepository, userRepository, employeeRepository, employeeMapper, permissionCacheEvictor);
        workTypes = new WorkTypeService(workTypeRepository, estateRepository,
                Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneId.of("Asia/Colombo")));
        divisions = new DivisionService(divisionRepository, estateRepository);
        fields = new FieldService(fieldRepository, divisionRepository, estateRepository);
        when(divisionRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(divisionRepository.findInEstate("S01", "D1")).thenReturn(Optional.of(division("S01", "D1")));
        when(divisionRepository.findInEstate("S01", "D2")).thenReturn(Optional.of(division("S01", "D2")));
        when(divisionRepository.findInEstate("S02", "D1")).thenReturn(Optional.of(division("S02", "D1")));
        when(divisionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(divisionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(fieldRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fieldRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(workTypeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(estateRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(estateRepository.existsById("S01")).thenReturn(true);
        when(estateRepository.existsById("S02")).thenReturn(true);
    }

    @Test
    void aValidSiteIsSaved() {
        Estate saved = estates.createEstate(site("S03", "Site Three", "23", "12", 2, "-2"));

        assertThat(saved.getId()).isEqualTo("S03");
        assertThat(saved.getVariancePercent()).isEqualByComparingTo("-2.00");
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void siteCodeIsSavedInCapitals() {
        assertThat(estates.createEstate(site(" s03 ", "Site", "23", "12", 2, "2")).getId()).isEqualTo("S03");
    }

    @Test
    void SB01_siteCodeMandatoryAndUpTo20Characters() {
        for (String code : new String[]{null, " ", "S".repeat(21)}) {
            assertThatThrownBy(() -> estates.createEstate(site(code, "Site", "23", "12", 2, "2")))
                    .hasMessage(SiteMessages.SB_01_SITE_CODE);
        }
    }

    @Test
    void SB02_siteCodeMustBeUnique() {
        assertThatThrownBy(() -> estates.createEstate(site("s01", "Site", "23", "12", 2, "2")))
                .hasMessage(SiteMessages.SB_02_SITE_EXISTS);
        verify(estateRepository, never()).save(any());
    }

    @Test
    void SB03_siteNameMandatory() {
        assertThatThrownBy(() -> estates.createEstate(site("S03", " ", "23", "12", 2, "2")))
                .hasMessage(SiteMessages.SB_03_SITE_NAME);
    }

    @Test
    void SB04_fullDayAbove0WithAtMost2Decimals() {
        for (String fullDay : new String[]{null, "0", "-1", "23.001"}) {
            assertThatThrownBy(() -> estates.createEstate(site("S03", "Site", fullDay, "12", 2, "2")))
                    .hasMessage(SiteMessages.SB_04_FULL_DAY);
        }
    }

    @Test
    void SB05_SB06_halfDayAbove0AndNotOverFullDay() {
        assertThatThrownBy(() -> estates.createEstate(site("S03", "Site", "23", "0", 2, "2")))
                .hasMessage(SiteMessages.SB_05_HALF_DAY);
        assertThatThrownBy(() -> estates.createEstate(site("S03", "Site", "23", "23.5", 2, "2")))
                .hasMessage(SiteMessages.SB_06_HALF_OVER_FULL);
        // Equal is allowed
        assertThat(estates.createEstate(site("S03", "Site", "23", "23", 2, "2"))).isNotNull();
    }

    @Test
    void SB07_maxTransactionsAtLeast1() {
        for (Integer max : new Integer[]{null, 0, -1}) {
            assertThatThrownBy(() -> estates.createEstate(site("S03", "Site", "23", "12", max, "2")))
                    .hasMessage(SiteMessages.SB_07_MAX_TRANSACTIONS);
        }
    }

    @Test
    void SB08_varianceMandatoryWithAtMost2DecimalsAndNoRangeLimit() {
        for (String variance : new String[]{null, "2.125"}) {
            assertThatThrownBy(() -> estates.createEstate(site("S03", "Site", "23", "12", 2, variance)))
                    .hasMessage(SiteMessages.SB_08_VARIANCE);
        }
        assertThat(estates.createEstate(site("S03", "Site", "23", "12", 2, "150.50")).getVariancePercent())
                .isEqualByComparingTo("150.50");
    }

    @Test
    void SB09_fieldUniqueWithinTheSite() {
        when(fieldRepository.existsById(new FieldId("S01", "CP1"))).thenReturn(true);

        assertThatThrownBy(() -> fields.createField("S01", new CreateFieldRequest("CP1", "D1", "North", false)))
                .hasMessage(SiteMessages.SB_09_FIELD_EXISTS);
        // Another site can have its own CP1
        assertThat(fields.createField("S02", new CreateFieldRequest("CP1", "D1", "North", false)).fieldCode())
                .isEqualTo("CP1");
    }

    @Test
    void SB09_fieldNeedsDescriptionAndADivisionOfTheSite() {
        assertThatThrownBy(() -> fields.createField("S01", new CreateFieldRequest("CP1", null, "North", false)))
                .hasMessage("Please select the Division.");
        assertThatThrownBy(() -> fields.createField("S01", new CreateFieldRequest("CP1", "D9", "North", false)))
                .hasMessageContaining("Division D9 not found");
        assertThatThrownBy(() -> fields.createField("S01", new CreateFieldRequest("CP1", "D1", " ", false)))
                .hasMessage("Please enter the Field Description.");
    }

    @Test
    void TC97_fieldsAreSavedUnderTheirDivision() {
        assertThat(fields.createField("S01", new CreateFieldRequest("CP1", "D1", "North", true)).divisionCode())
                .isEqualTo("D1");
        assertThat(fields.createField("S01", new CreateFieldRequest("CP2", "D2", "South", true)).divisionCode())
                .isEqualTo("D2");
        // SB-10: each new default only clears the other defaults of its own division
        verify(fieldRepository).clearDefaultExcept("S01", "D1", "CP1");
        verify(fieldRepository).clearDefaultExcept("S01", "D2", "CP2");
    }

    @Test
    void SB10_aFieldThatIsNotTheDefaultClearsNothing() {
        fields.createField("S01", new CreateFieldRequest("CP1", "D1", "North", false));

        verify(fieldRepository, never()).clearDefaultExcept(anyString(), anyString(), anyString());
    }

    @Test
    void aFieldCanMoveToAnotherDivisionAndTakesItsEmployeeDefaultsAlong() {
        Field cp1 = field("CP1", "D1");
        when(fieldRepository.findInEstate("S01", "CP1")).thenReturn(Optional.of(cp1));

        fields.updateField("S01", "CP1", new UpdateFieldRequest(null, "D2", "North", null));

        assertThat(cp1.getDivisionCode()).isEqualTo("D2");
        verify(fieldRepository).moveEmployeeDefaults("S01", "CP1", "D2");
    }

    @Test
    void SB12_aUsedFieldKeepsItsCode() {
        when(fieldRepository.findInEstate("S01", "CP1")).thenReturn(Optional.of(field("CP1", "D1")));
        when(fieldRepository.findEmployeesUsingAsDefault(anyString(), anyList())).thenReturn(List.of());
        when(fieldRepository.isUsedInAttendance("S01", List.of("CP1"))).thenReturn(true);

        assertThatThrownBy(() -> fields.updateField("S01", "CP1", new UpdateFieldRequest("CP9", null, "North", null)))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        verify(fieldRepository, never()).changeCode(anyString(), anyString(), anyString());
    }

    @Test
    void SB11_workTypeNameUniqueWithinTheSite() {
        when(workTypeRepository.nameExists("S01", "Plucking", "WT02")).thenReturn(true);

        assertThatThrownBy(() -> workTypes.createWorkType("S01", new CreateWorkTypeRequest("WT02", "Plucking", true)))
                .hasMessage(SiteMessages.SB_11_WORK_TYPE_EXISTS);
    }

    @Test
    void TC99_SB16_workTypeIdMandatoryUniqueWithinTheSiteAndInCapitals() {
        when(workTypeRepository.existsById(new WorkTypeId("S01", "WT01"))).thenReturn(true);

        assertThatThrownBy(() -> workTypes.createWorkType("S01", new CreateWorkTypeRequest(" ", "Plucking", true)))
                .hasMessage("Please enter the Work Type ID.");
        assertThatThrownBy(() -> workTypes.createWorkType("S01", new CreateWorkTypeRequest("wt01", "Plucking", true)))
                .hasMessage(SiteMessages.SB_16_WORK_TYPE_ID_EXISTS);
        assertThatThrownBy(() -> workTypes.createWorkType("S01",
                new CreateWorkTypeRequest("W".repeat(21), "Plucking", true)))
                .hasMessageContaining("at most 20");
        // Both sites can have WT01
        assertThat(workTypes.createWorkType("S02", new CreateWorkTypeRequest("wt01", " Plucking ", true)))
                .satisfies(created -> {
                    assertThat(created.workTypeCode()).isEqualTo("WT01");
                    assertThat(created.name()).isEqualTo("Plucking");
                });
    }

    @Test
    void SB12_aUsedWorkTypeKeepsItsName() {
        WorkType plucking = workType("WT01", "Plucking", true);
        when(workTypeRepository.findInEstate("S01", "WT01")).thenReturn(Optional.of(plucking));
        when(workTypeRepository.isUsedInAttendance("S01", List.of("WT01"))).thenReturn(true);

        assertThatThrownBy(() -> workTypes.updateWorkType("S01", "WT01", new UpdateWorkTypeRequest("Tea plucking", true)))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        // Weighing Required can still change
        workTypes.updateWorkType("S01", "WT01", new UpdateWorkTypeRequest("Plucking", false));
        assertThat(plucking.isWeighingRequired()).isFalse();
    }

    @Test
    void TC98_SB15_divisionCodeUniqueWithinTheSiteWithDescription() {
        when(divisionRepository.existsById(new DivisionId("S01", "D1"))).thenReturn(true);

        assertThatThrownBy(() -> divisions.createDivision("S01", new CreateDivisionRequest("D1", "North")))
                .hasMessage(SiteMessages.SB_15_DIVISION_EXISTS);
        assertThatThrownBy(() -> divisions.createDivision("S01", new CreateDivisionRequest(" ", "North")))
                .hasMessage("Please enter the Division Code.");
        assertThatThrownBy(() -> divisions.createDivision("S01", new CreateDivisionRequest("D3", " ")))
                .hasMessage("Please enter the Division Description.");
        // Another site can have its own D1
        assertThat(divisions.createDivision("S02", new CreateDivisionRequest("D1", "North")).divisionCode())
                .isEqualTo("D1");
    }

    @Test
    void TC100_SB17_aDivisionWithFieldsCannotBeRemoved() {
        Division d1 = division("S01", "D1");
        when(divisionRepository.findAllInEstate("S01", List.of("D1"))).thenReturn(List.of(d1));
        when(divisionRepository.hasFields("S01", List.of("D1"))).thenReturn(true);

        assertThatThrownBy(() -> divisions.deleteDivisions("S01", List.of("D1")))
                .hasMessage(SiteMessages.SB_17_DIVISION_HAS_FIELDS);
        verify(divisionRepository, never()).deleteAll(anyList());

        when(divisionRepository.hasFields("S01", List.of("D1"))).thenReturn(false);
        divisions.deleteDivisions("S01", List.of("D1"));
        verify(divisionRepository).deleteAll(List.of(d1));
    }

    @Test
    void SB12_aDivisionInUseCannotBeRemovedOrChangeItsCode() {
        when(divisionRepository.findAllInEstate("S01", List.of("D1"))).thenReturn(List.of(division("S01", "D1")));
        when(divisionRepository.isUsed("S01", List.of("D1"))).thenReturn(true);

        assertThatThrownBy(() -> divisions.deleteDivisions("S01", List.of("D1")))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        assertThatThrownBy(() -> divisions.updateDivision("S01", "D1", new UpdateDivisionRequest("D9", "North")))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        verify(divisionRepository, never()).changeCode(anyString(), anyString(), anyString());
    }

    @Test
    void anUnusedDivisionCanChangeItsCode() {
        when(divisionRepository.findInEstate("S01", "D9")).thenReturn(Optional.of(division("S01", "D9")));

        assertThat(divisions.updateDivision("S01", "D1", new UpdateDivisionRequest("D9", "North")).divisionCode())
                .isEqualTo("D9");
        verify(divisionRepository).changeCode(eq("S01"), eq("D1"), eq("D9"));
    }

    @Test
    void SB12_fieldUsedInAttendanceCannotBeRemoved() {
        Field cp1 = field("CP1", "D1");
        when(fieldRepository.findAllInEstate("S01", List.of("CP1"))).thenReturn(List.of(cp1));
        when(fieldRepository.findEmployeesUsingAsDefault(anyString(), anyList())).thenReturn(List.of());
        when(fieldRepository.isUsedInAttendance("S01", List.of("CP1"))).thenReturn(true);

        assertThatThrownBy(() -> fields.deleteFields("S01", List.of("CP1")))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        verify(fieldRepository, never()).deleteAll(anyList());
    }

    @Test
    void SB12_workTypeUsedInAttendanceCannotBeRemoved() {
        WorkType pruning = workType("PRUNING", "Pruning", false);
        when(workTypeRepository.findAllInEstate("S01", List.of("PRUNING"))).thenReturn(List.of(pruning));
        when(workTypeRepository.isUsedInAttendance("S01", List.of("PRUNING"))).thenReturn(true);

        assertThatThrownBy(() -> workTypes.deleteWorkTypes("S01", List.of("PRUNING")))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        verify(workTypeRepository, never()).deleteAll(anyList());

        when(workTypeRepository.isUsedInAttendance("S01", List.of("PRUNING"))).thenReturn(false);
        workTypes.deleteWorkTypes("S01", List.of("PRUNING"));
        verify(workTypeRepository).deleteAll(List.of(pruning));
    }

    @Test
    void SB13_weighingRequiredCannotBeTurnedOffAfterWeighingToday() {
        WorkType plucking = workType("PLUCKING", "Plucking", true);
        when(workTypeRepository.findInEstate("S01", "PLUCKING")).thenReturn(Optional.of(plucking));
        when(workTypeRepository.isWeighedOn("S01", "PLUCKING", TODAY)).thenReturn(true);

        assertThatThrownBy(() -> workTypes.updateWorkType("S01", "PLUCKING", new UpdateWorkTypeRequest("Plucking", false)))
                .hasMessage(SiteMessages.SB_13_WEIGHED_TODAY);
        assertThat(plucking.isWeighingRequired()).isTrue();

        // Turning it on is always allowed; with no weighing today it can be turned off
        when(workTypeRepository.isWeighedOn("S01", "PLUCKING", TODAY)).thenReturn(false);
        workTypes.updateWorkType("S01", "PLUCKING", new UpdateWorkTypeRequest("Plucking", false));
        assertThat(plucking.isWeighingRequired()).isFalse();
    }

    private static Division division(String estateId, String code) {
        Division division = new Division();
        division.setEstateId(estateId);
        division.setDivisionCode(code);
        division.setDescription("Division " + code);
        return division;
    }

    private static Field field(String code, String divisionCode) {
        Field field = new Field();
        field.setEstateId("S01");
        field.setFieldCode(code);
        field.setDivisionCode(divisionCode);
        field.setDescription("Field " + code);
        return field;
    }

    private static WorkType workType(String code, String name, boolean weighingRequired) {
        WorkType workType = new WorkType();
        workType.setEstateId("S01");
        workType.setWorkTypeCode(code);
        workType.setName(name);
        workType.setWeighingRequired(weighingRequired);
        return workType;
    }

    private static CreateEstateRequest site(String id, String name, String fullDay, String halfDay,
                                            Integer max, String variance) {
        return new CreateEstateRequest(id, name, null, null,
                fullDay == null ? null : new BigDecimal(fullDay),
                halfDay == null ? null : new BigDecimal(halfDay),
                max,
                variance == null ? null : new BigDecimal(variance));
    }
}
