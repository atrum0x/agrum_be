package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.division.Division;
import com.atrum.agrum.division.DivisionRepository;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.field.Field;
import com.atrum.agrum.field.FieldRepository;
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
 * Working site, work type, division and field for a batch or a correction (AT-05, AT-07, AT-09,
 * AT-10, AT-19, TC-20, TC-102, TC-103). A site the user is not granted has no visible work types,
 * divisions or fields (estate filter), so it is refused as AT-10, which the repository mocks stand in
 * for here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSiteValidatorTest {

    @Mock
    private EstateRepository estateRepository;
    @Mock
    private WorkTypeRepository workTypeRepository;
    @Mock
    private DivisionRepository divisionRepository;
    @Mock
    private FieldRepository fieldRepository;

    private AttendanceSiteValidator validator;
    private Estate s01;

    @BeforeEach
    void setUp() {
        validator = new AttendanceSiteValidator(estateRepository, workTypeRepository, divisionRepository,
                fieldRepository);
        s01 = new Estate();
        s01.setId("S01");
        s01.setActive(true);
        when(estateRepository.findById("S01")).thenReturn(Optional.of(s01));
        when(estateRepository.findById("S99")).thenReturn(Optional.empty());

        WorkType plucking = new WorkType();
        plucking.setWorkTypeCode("PLUCKING");
        Division d1 = division("D1");
        Division d2 = division("D2");
        when(workTypeRepository.existsInEstate("S01")).thenReturn(true);
        when(divisionRepository.existsInEstate("S01")).thenReturn(true);
        when(fieldRepository.existsInEstate("S01")).thenReturn(true);
        when(workTypeRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(divisionRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(fieldRepository.findInEstate(anyString(), anyString())).thenReturn(Optional.empty());
        when(workTypeRepository.findInEstate("S01", "PLUCKING")).thenReturn(Optional.of(plucking));
        when(divisionRepository.findInEstate("S01", "D1")).thenReturn(Optional.of(d1));
        when(divisionRepository.findInEstate("S01", "D2")).thenReturn(Optional.of(d2));
        when(fieldRepository.findInEstate("S01", "CP1")).thenReturn(Optional.of(field("CP1", d1)));
        when(fieldRepository.findInEstate("S01", "CP2")).thenReturn(Optional.of(field("CP2", d2)));
    }

    @Test
    void aCompleteSiteResolvesWithTrimmedCodes() {
        AttendanceSite site = validator.resolve(" S01 ", " PLUCKING ", " D1 ", " CP1 ");

        assertThat(site.estate()).isSameAs(s01);
        assertThat(site.workType().getWorkTypeCode()).isEqualTo("PLUCKING");
        assertThat(site.division().getDivisionCode()).isEqualTo("D1");
        assertThat(site.field().getFieldCode()).isEqualTo("CP1");
    }

    @Test
    void AT05_missingOrUnknownSite_isRefused() {
        for (String estateId : new String[]{null, " ", "S99"}) {
            assertThatThrownBy(() -> validator.resolve(estateId, "PLUCKING", "D1", "CP1"))
                    .hasMessage(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
        }
    }

    @Test
    void TC20_AT05_inactiveSite_isRefused() {
        s01.setActive(false);

        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", "CP1"))
                .hasMessage(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
    }

    @Test
    void AT10_siteWithoutFieldsDivisionsOrWorkTypes_isRefused() {
        when(fieldRepository.existsInEstate("S01")).thenReturn(false);
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", "CP1"))
                .hasMessage(AttendanceMessages.AT_10_SITE_SETUP);

        when(fieldRepository.existsInEstate("S01")).thenReturn(true);
        when(divisionRepository.existsInEstate("S01")).thenReturn(false);
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", "CP1"))
                .hasMessage(AttendanceMessages.AT_10_SITE_SETUP);

        when(divisionRepository.existsInEstate("S01")).thenReturn(true);
        when(workTypeRepository.existsInEstate("S01")).thenReturn(false);
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", "CP1"))
                .hasMessage(AttendanceMessages.AT_10_SITE_SETUP);
    }

    @Test
    void AT07_missingWorkTypeOrOneFromAnotherSite_isRefused() {
        for (String workType : new String[]{null, "", "PLUCKING_S02"}) {
            assertThatThrownBy(() -> validator.resolve("S01", workType, "D1", "CP1"))
                    .hasMessage(AttendanceMessages.AT_07_WORK_TYPE);
        }
    }

    @Test
    void TC103_AT19_missingDivisionOrOneFromAnotherSite_isRefused() {
        for (String divisionCode : new String[]{null, "", "D_S02"}) {
            assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", divisionCode, "CP1"))
                    .hasMessage(AttendanceMessages.AT_19_DIVISION);
        }
    }

    @Test
    void AT09_missingFieldOrOneFromAnotherSite_isRefused() {
        for (String fieldCode : new String[]{null, "", "CP_S02"}) {
            assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", fieldCode))
                    .hasMessage(AttendanceMessages.AT_09_FIELD);
        }
    }

    @Test
    void TC102_AT09_fieldOfAnotherDivision_isRefused() {
        assertThatThrownBy(() -> validator.resolve("S01", "PLUCKING", "D1", "CP2"))
                .hasMessage(AttendanceMessages.AT_09_FIELD);

        assertThat(validator.resolve("S01", "PLUCKING", "D2", "CP2").field().getFieldCode()).isEqualTo("CP2");
    }

    private static Division division(String code) {
        Division division = new Division();
        division.setEstateId("S01");
        division.setDivisionCode(code);
        division.setDescription("Division " + code);
        return division;
    }

    private static Field field(String code, Division division) {
        Field field = new Field();
        field.setEstateId("S01");
        field.setFieldCode(code);
        field.setDivision(division);
        return field;
    }
}
