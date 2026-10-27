package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.workType.WorkType;
import com.atrum.agrum.workType.WorkTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Working site, work type and collection point for a batch or a correction (AT-05, AT-07, AT-09,
 * AT-10, TC-20). A site the user is not granted has no visible work types or collection points
 * (estate filter), so it is refused as AT-10, which the repository mocks stand in for here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSiteValidatorTest {

    @Mock
    private EstateRepository estateRepository;
    @Mock
    private WorkTypeRepository workTypeRepository;
    @Mock
    private CollectionPointRepository collectionPointRepository;

    private AttendanceSiteValidator validator;
    private Estate s01;

    @BeforeEach
    void setUp() {
        validator = new AttendanceSiteValidator(estateRepository, workTypeRepository, collectionPointRepository);
        s01 = new Estate();
        s01.setId("S01");
        s01.setActive(true);
        when(estateRepository.findById("S01")).thenReturn(Optional.of(s01));
        when(estateRepository.findById("S99")).thenReturn(Optional.empty());

        WorkType plucking = new WorkType();
        plucking.setWorkType("PLUCKING");
        CollectionPoint cp1 = new CollectionPoint();
        cp1.setCpCode("CP1");
        when(workTypeRepository.existsInEstate("S01")).thenReturn(true);
        when(collectionPointRepository.existsInEstate("S01")).thenReturn(true);
        when(workTypeRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(collectionPointRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(workTypeRepository.findInEstate("S01", "PLUCKING")).thenReturn(Optional.of(plucking));
        when(collectionPointRepository.findInEstate("S01", "CP1")).thenReturn(Optional.of(cp1));
    }

    @Test
    void aCompleteSiteResolvesWithTrimmedCodes() {
        AttendanceSite site = validator.resolve(" S01 ", " PLUCKING ", " CP1 ");

        assertThat(site.estate()).isSameAs(s01);
        assertThat(site.workType().getWorkType()).isEqualTo("PLUCKING");
        assertThat(site.collectionPoint().getCpCode()).isEqualTo("CP1");
    }

    @Test
    void AT05_missingOrUnknownSite_isRefused() {
        for (String estateId : new String[]{null, " ", "S99"}) {
            assertThatThrownBy(() -> validator.resolve(estateId, "PLUCKING", "CP1"))
                    .hasMessage(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
        }
    }

    @Test
    void TC20_AT05_inactiveSite_isRefused() {
        s01.setActive(false);

        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "CP1"))
                .hasMessage(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
    }

    @Test
    void AT10_siteWithoutCollectionPointsOrWorkTypes_isRefused() {
        when(collectionPointRepository.existsInEstate("S01")).thenReturn(false);
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "CP1"))
                .hasMessage(AttendanceMessages.AT_10_SITE_SETUP);

        when(collectionPointRepository.existsInEstate("S01")).thenReturn(true);
        when(workTypeRepository.existsInEstate("S01")).thenReturn(false);
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "CP1"))
                .hasMessage(AttendanceMessages.AT_10_SITE_SETUP);
    }

    @Test
    void AT07_missingWorkTypeOrOneFromAnotherSite_isRefused() {
        for (String workType : new String[]{null, "", "PLUCKING_S02"}) {
            assertThatThrownBy(() -> validator.resolve("S01", workType, "CP1"))
                    .hasMessage(AttendanceMessages.AT_07_WORK_TYPE);
        }
    }

    @Test
    void AT09_missingCollectionPointOrOneFromAnotherSite_isRefused() {
        for (String cpCode : new String[]{null, "", "CP_S02"}) {
            assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", cpCode))
                    .hasMessage(AttendanceMessages.AT_09_COLLECTION_POINT);
        }
    }
}
