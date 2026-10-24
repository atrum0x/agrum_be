package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.attendance.dto.AttendanceCorrectionDto;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.AttendanceSnapshot;
import com.atrum.agrum.attendance.dto.CorrectAttendanceRequest;
import com.atrum.agrum.attendance.dto.DeleteAttendanceRequest;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Correct / Delete for administrators (web only). AT-14 is enforced by granting the
 * correct and delete endpoints only in the "Correct Attendance" permission set.
 * Site access comes from the estate filter on Attendance and AttendanceCorrection.
 */
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    private static final int MAX_REASON_LENGTH = 300;

    private final AttendanceRepository attendanceRepository;
    private final AttendanceCorrectionRepository attendanceCorrectionRepository;
    private final AttendanceSiteValidator siteValidator;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /** Worker and date are never corrected; delete and re-record today instead. */
    @Transactional
    public AttendanceDto correctAttendance(UUID attendanceId, CorrectAttendanceRequest request) {
        AppUser user = userService.getCurrentUser();
        Attendance attendance = findVisible(attendanceId);
        String reason = requireReason(request.reason());
        checkVersion(attendance, request.rowVersion());
        AttendanceSite site = siteValidator.resolve(request.estateId(), request.workType(), request.cpCode());
        // AT-08
        if (request.attendanceCategory() == null) {
            throw new IllegalArgumentException(AttendanceMessages.AT_08_CATEGORY);
        }
        // TODO(weighings) AT-16: when weight records exist, refuse a non-weighing work type
        //  while the attendance has non-cancelled weighings (AttendanceMessages.AT_16_WEIGHINGS_EXIST)

        AttendanceSnapshot oldValues = AttendanceSnapshot.of(attendance);
        attendance.setEstateId(site.estate().getId());
        // Read-only mapping; set so the returned DTO has the new estate without a reload
        attendance.setEstate(site.estate());
        attendance.setWorkType(site.workType());
        attendance.setCollectionPoint(site.collectionPoint());
        attendance.setAttendanceCategory(request.attendanceCategory());
        AttendanceSnapshot newValues = AttendanceSnapshot.of(attendance);
        if (newValues.equals(oldValues)) {
            throw new IllegalArgumentException("Nothing was changed.");
        }

        // TODO(weighings) (Proposed): copy the site, collection point and work type to the linked
        //  weight records in this transaction. The 10 pm summary finds the changed date via corrected_at.
        attendanceRepository.saveAndFlush(attendance);
        AttendanceCorrection correction = saveCorrection(
                attendanceId, CorrectionAction.CORRECT, oldValues, newValues, reason, user);
        return AttendanceDto.from(attendance, correction);
    }

    /** For a wrong worker or date. The history row keeps the deleted values. */
    @Transactional
    public void deleteAttendance(UUID attendanceId, DeleteAttendanceRequest request) {
        AppUser user = userService.getCurrentUser();
        Attendance attendance = findVisible(attendanceId);
        String reason = requireReason(request.reason());
        checkVersion(attendance, request.rowVersion());
        // TODO(weighings) AT-17 (Proposed): refuse while the attendance has non-cancelled
        //  weighings (AttendanceMessages.AT_17_CANNOT_DELETE)

        AttendanceSnapshot oldValues = AttendanceSnapshot.of(attendance);
        attendanceRepository.delete(attendance);
        attendanceRepository.flush();
        saveCorrection(attendanceId, CorrectionAction.DELETE, oldValues, null, reason, user);
    }

    /** All corrections at the user's sites, newest first. Also works after a Delete. */
    @Transactional(readOnly = true)
    public List<AttendanceCorrectionDto> getHistory(UUID attendanceId) {
        return attendanceCorrectionRepository.findAllByAttendanceIdOrderByCorrectedAtDesc(attendanceId).stream()
                .map(correction -> new AttendanceCorrectionDto(
                        correction.getId(),
                        correction.getAttendanceId(),
                        correction.getAction(),
                        readSnapshot(correction.getOldValues()),
                        correction.getNewValues() == null ? null : readSnapshot(correction.getNewValues()),
                        correction.getReason(),
                        correction.getCorrectedBy().getUsername(),
                        correction.getCorrectedAt()))
                .toList();
    }

    private Attendance findVisible(UUID attendanceId) {
        return attendanceRepository.findVisibleById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance not found: " + attendanceId));
    }

    // AT-15
    private static String requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException(AttendanceMessages.AT_15_REASON);
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    // AT-18: the record must be unchanged since the user opened it
    private static void checkVersion(Attendance attendance, Integer rowVersion) {
        if (rowVersion == null || !rowVersion.equals(attendance.getRowVersion())) {
            throw new ObjectOptimisticLockingFailureException(Attendance.class, attendance.getAttendanceId());
        }
    }

    private AttendanceCorrection saveCorrection(UUID attendanceId, CorrectionAction action,
                                                AttendanceSnapshot oldValues, AttendanceSnapshot newValues,
                                                String reason, AppUser user) {
        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendanceId(attendanceId);
        correction.setEstateId(newValues != null ? newValues.estateId() : oldValues.estateId());
        correction.setAction(action);
        correction.setOldValues(objectMapper.writeValueAsString(oldValues));
        correction.setNewValues(newValues == null ? null : objectMapper.writeValueAsString(newValues));
        correction.setReason(reason);
        correction.setCorrectedBy(user);
        return attendanceCorrectionRepository.save(correction);
    }

    private AttendanceSnapshot readSnapshot(String json) {
        return objectMapper.readValue(json, AttendanceSnapshot.class);
    }
}
