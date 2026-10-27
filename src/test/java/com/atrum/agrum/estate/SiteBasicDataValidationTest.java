package com.atrum.agrum.estate;

import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.collectionPoint.CollectionPointId;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.collectionPoint.CollectionPointService;
import com.atrum.agrum.collectionPoint.dto.CreateCollectionPointRequest;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.mapper.EmployeeMapper;
import com.atrum.agrum.estate.dto.CreateEstateRequest;
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
import org.springframework.data.redis.core.StringRedisTemplate;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Site Basic Data checks SB-01 to SB-13 (FSD 2.3). SB-10 (one default collection point) is kept by
 * CollectionPointService.setDefault; SB-14 is the endpoint grant.
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
    private StringRedisTemplate redisTemplate;
    @Mock
    private WorkTypeRepository workTypeRepository;
    @Mock
    private CollectionPointRepository collectionPointRepository;

    private EstateService estates;
    private WorkTypeService workTypes;
    private CollectionPointService collectionPoints;

    @BeforeEach
    void setUp() {
        estates = new EstateService(estateRepository, userRepository, employeeRepository, employeeMapper, redisTemplate);
        workTypes = new WorkTypeService(workTypeRepository, estateRepository,
                Clock.fixed(Instant.parse("2026-10-24T04:30:00Z"), ZoneId.of("Asia/Colombo")));
        collectionPoints = new CollectionPointService(collectionPointRepository, estateRepository);
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
    void SB09_collectionPointUniqueWithinTheSite() {
        when(collectionPointRepository.existsById(new CollectionPointId("S01", "CP1"))).thenReturn(true);
        when(collectionPointRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> collectionPoints.createCollectionPoint("S01", new CreateCollectionPointRequest("CP1", "North", null, false)))
                .hasMessage(SiteMessages.SB_09_COLLECTION_POINT_EXISTS);
        // Another site can have its own CP1
        assertThat(collectionPoints.createCollectionPoint("S02", new CreateCollectionPointRequest("CP1", "North", null, false)).cpCode())
                .isEqualTo("CP1");
    }

    @Test
    void SB11_workTypeUniqueWithinTheSite() {
        when(workTypeRepository.existsById(new WorkTypeId("S01", "PLUCKING"))).thenReturn(true);
        when(workTypeRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> workTypes.createWorkType("S01", new CreateWorkTypeRequest("PLUCKING", true)))
                .hasMessage(SiteMessages.SB_11_WORK_TYPE_EXISTS);
        // Both sites can have PLUCKING
        assertThat(workTypes.createWorkType("S02", new CreateWorkTypeRequest("PLUCKING", true)).workType())
                .isEqualTo("PLUCKING");
    }

    @Test
    void SB12_collectionPointUsedInAttendanceCannotBeRemoved() {
        CollectionPoint cp1 = new CollectionPoint();
        cp1.setCpCode("CP1");
        cp1.setEstateId("S01");
        when(collectionPointRepository.findAllInEstate("S01", List.of("CP1"))).thenReturn(List.of(cp1));
        when(collectionPointRepository.findEmployeesUsingAsDefault(anyString(), anyList())).thenReturn(List.of());
        when(collectionPointRepository.isUsedInAttendance("S01", List.of("CP1"))).thenReturn(true);

        assertThatThrownBy(() -> collectionPoints.deleteCollectionPoints("S01", List.of("CP1")))
                .hasMessage(SiteMessages.SB_12_IN_USE);
        verify(collectionPointRepository, never()).deleteAll(anyList());
    }

    @Test
    void SB12_workTypeUsedInAttendanceCannotBeRemoved() {
        WorkType pruning = new WorkType();
        pruning.setWorkType("PRUNING");
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
        WorkType plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        plucking.setEstateId("S01");
        plucking.setWeighingRequired(true);
        when(workTypeRepository.findInEstate("S01", "PLUCKING")).thenReturn(Optional.of(plucking));
        when(workTypeRepository.isWeighedOn("S01", "PLUCKING", TODAY)).thenReturn(true);

        assertThatThrownBy(() -> workTypes.updateWorkType("S01", "PLUCKING", new UpdateWorkTypeRequest(false)))
                .hasMessage(SiteMessages.SB_13_WEIGHED_TODAY);
        assertThat(plucking.isWeighingRequired()).isTrue();

        // Turning it on is always allowed; with no weighing today it can be turned off
        when(workTypeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(workTypeRepository.isWeighedOn("S01", "PLUCKING", TODAY)).thenReturn(false);
        workTypes.updateWorkType("S01", "PLUCKING", new UpdateWorkTypeRequest(false));
        assertThat(plucking.isWeighingRequired()).isFalse();
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
