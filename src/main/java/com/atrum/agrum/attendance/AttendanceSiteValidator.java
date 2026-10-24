package com.atrum.agrum.attendance;

import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.collectionPoint.CollectionPointRepository;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.estate.EstateRepository;
import com.atrum.agrum.workType.WorkType;
import com.atrum.agrum.workType.WorkTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Checks the working site, work type and collection point shared by recording and correcting.
 */
@Component
@RequiredArgsConstructor
public class AttendanceSiteValidator {

    private final EstateRepository estateRepository;
    private final WorkTypeRepository workTypeRepository;
    private final CollectionPointRepository collectionPointRepository;

    public record AttendanceSite(Estate estate, WorkType workType, CollectionPoint collectionPoint) {}

    /**
     * Must run inside a @Transactional method: the work type and collection point lookups are
     * estate-filtered, so a site not granted to the user has none and is refused (AT-10).
     */
    public AttendanceSite resolve(String estateId, String workTypeCode, String cpCode) {
        // AT-05: mandatory and Active. Visibility comes from the estate filter below
        if (!StringUtils.hasText(estateId)) {
            throw new IllegalArgumentException(AttendanceMessages.AT_05_SITE_NOT_ALLOWED);
        }
        Estate estate = estateRepository.findById(estateId.trim())
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .orElseThrow(() -> new IllegalArgumentException(AttendanceMessages.AT_05_SITE_NOT_ALLOWED));

        // AT-10
        if (!workTypeRepository.existsInEstate(estate.getId())
                || !collectionPointRepository.existsInEstate(estate.getId())) {
            throw new IllegalArgumentException(AttendanceMessages.AT_10_SITE_SETUP);
        }

        // AT-07
        WorkType workType = StringUtils.hasText(workTypeCode)
                ? workTypeRepository.findInEstate(estate.getId(), workTypeCode.trim()).orElse(null)
                : null;
        if (workType == null) {
            throw new IllegalArgumentException(AttendanceMessages.AT_07_WORK_TYPE);
        }

        // AT-09
        CollectionPoint collectionPoint = StringUtils.hasText(cpCode)
                ? collectionPointRepository.findInEstate(estate.getId(), cpCode.trim()).orElse(null)
                : null;
        if (collectionPoint == null) {
            throw new IllegalArgumentException(AttendanceMessages.AT_09_COLLECTION_POINT);
        }

        return new AttendanceSite(estate, workType, collectionPoint);
    }
}
