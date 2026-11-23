package com.atrum.agrum.attendance;

import com.atrum.agrum.division.Division;
import com.atrum.agrum.division.DivisionRepository;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.field.Field;
import com.atrum.agrum.field.FieldRepository;
import com.atrum.agrum.workType.WorkType;
import com.atrum.agrum.workType.WorkTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Checks the working site, work type, division and field shared by recording and correcting.
 */
@Component
@RequiredArgsConstructor
public class AttendanceSiteValidator {

    private final EstateRepository estateRepository;
    private final WorkTypeRepository workTypeRepository;
    private final DivisionRepository divisionRepository;
    private final FieldRepository fieldRepository;

    public record AttendanceSite(Estate estate, WorkType workType, Division division, Field field) {}

    /**
     * Must run inside a @Transactional method: the work type, division and field lookups are
     * estate-filtered, so a site not granted to the user has none and is refused (AT-10).
     */
    public AttendanceSite resolve(String estateId, String workTypeCode, String divisionCode, String fieldCode) {
        // AT-05: mandatory and Active. Visibility comes from the estate filter below
        if (!StringUtils.hasText(estateId)) {
            throw new IllegalArgumentException(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
        }
        Estate estate = estateRepository.findById(estateId.trim())
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .orElseThrow(() -> new IllegalArgumentException(AttendanceMessages.AT_05_SITE_NOT_ALLOWED));

        // AT-10
        if (!workTypeRepository.existsInEstate(estate.getId())
                || !divisionRepository.existsInEstate(estate.getId())
                || !fieldRepository.existsInEstate(estate.getId())) {
            throw new IllegalArgumentException(AttendanceMessages.AT_10_SITE_SETUP);
        }

        // AT-07
        WorkType workType = StringUtils.hasText(workTypeCode)
                ? workTypeRepository.findInEstate(estate.getId(), workTypeCode.trim()).orElse(null)
                : null;
        if (workType == null) {
            throw new IllegalArgumentException(AttendanceMessages.AT_07_WORK_TYPE);
        }

        // AT-19
        Division division = StringUtils.hasText(divisionCode)
                ? divisionRepository.findInEstate(estate.getId(), divisionCode.trim()).orElse(null)
                : null;
        if (division == null) {
            throw new IllegalArgumentException(AttendanceMessages.AT_19_DIVISION);
        }

        // AT-09: a field of the selected division
        Field field = StringUtils.hasText(fieldCode)
                ? fieldRepository.findInEstate(estate.getId(), fieldCode.trim()).orElse(null)
                : null;
        if (field == null || !field.getDivisionCode().equals(division.getDivisionCode())) {
            throw new IllegalArgumentException(AttendanceMessages.AT_09_FIELD);
        }

        return new AttendanceSite(estate, workType, division, field);
    }
}
