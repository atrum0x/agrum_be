package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.Attendance;
import com.atrum.agrum.attendance.AttendanceRepository;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.RecordWeightRequest;
import com.atrum.agrum.weight.dto.SyncWeightResult;
import com.atrum.agrum.weight.dto.WeighableWorkerDto;
import com.atrum.agrum.weight.dto.WeighingDefaultsDto;
import com.atrum.agrum.weight.dto.WeightRecordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enter Weight Management (FSD 2.7): the weighing screen and saving a weighing.
 * Weighings need no approval; they can only be changed later through Weight History.
 */
@Service
@RequiredArgsConstructor
public class WeighingService {

    // A phone clock slightly ahead of the server is not a future record
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    // Keeps the IN list well under Postgres' bind parameter limit
    private static final int LOOKUP_CHUNK_SIZE = 1000;

    private final WeightRecordRepository weightRecordRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WeighingDefaultsDto getDefaults() {
        return new WeighingDefaultsDto(LocalDate.now(clock), clock.instant());
    }

    /**
     * Worker picker: attendance today at a visible, active site with a weighing-required
     * work type (WE-02, WE-03, WE-05). Workers at their limit are listed, with Save blocked on the phone.
     */
    @Transactional(readOnly = true)
    public List<WeighableWorkerDto> getWeighableWorkers(String search) {
        LocalDate today = LocalDate.now(clock);
        String term = StringUtils.hasText(search) ? search.trim() : "";
        List<Attendance> attendances = attendanceRepository.findWeighable(today, term);
        Map<String, WeightRecordRepository.DayCount> counts = countByEmployee(today,
                attendances.stream().map(attendance -> attendance.getEmployee().getEmployeeCode()).toList());

        return attendances.stream()
                .map(attendance -> {
                    String employeeCode = attendance.getEmployee().getEmployeeCode();
                    WeightRecordRepository.DayCount count = counts.get(employeeCode);
                    Estate estate = attendance.getEstate();
                    return new WeighableWorkerDto(
                            employeeCode,
                            attendance.getEmployee().getFullName(),
                            attendance.getAttendanceId(),
                            estate.getId(),
                            estate.getName(),
                            attendance.getCollectionPoint().getCpCode(),
                            attendance.getCollectionPoint().getName(),
                            attendance.getWorkType().getWorkType(),
                            WeightCalculator.variance(estate.getVariancePercent()),
                            count == null ? 0 : count.getActiveCount().intValue(),
                            estate.getMaxDailyTransactions(),
                            count == null || count.getLastSequence() == null ? 0 : count.getLastSequence());
                })
                .toList();
    }

    /** Phone, online: one weighing; a failed check is returned as an error. */
    @Transactional
    public WeightRecordDto recordWeight(RecordWeightRequest request) {
        return WeightRecordDto.from(record(request));
    }

    /**
     * One phone record in its own transaction. Called by WeightSyncService,
     * which turns a thrown validation error into a Rejected result.
     */
    @Transactional
    public SyncWeightResult recordSynced(RecordWeightRequest request) {
        return SyncWeightResult.sent(record(request));
    }

    private WeightRecord record(RecordWeightRequest request) {
        if (request.weightId() == null) {
            throw new IllegalArgumentException("Weight ID is required");
        }
        // WE-09: already received, e.g. the reply to an earlier send was lost
        Optional<WeightRecord> existing = weightRecordRepository.findById(request.weightId());
        if (existing.isPresent()) {
            return existing.get();
        }
        AppUser user = userService.getCurrentUser();

        // WE-01
        if (!StringUtils.hasText(request.employeeCode())) {
            throw new IllegalArgumentException(WeightMessages.WE_01_SELECT_WORKER);
        }
        String employeeCode = request.employeeCode().trim();
        // WE-06
        if (!WeightCalculator.isValidWeight(request.weightKg())) {
            throw new IllegalArgumentException(WeightMessages.WE_06_WEIGHT);
        }
        if (request.entryType() == null) {
            throw new IllegalArgumentException(WeightMessages.ENTRY_TYPE);
        }
        // TODO(decision CL-03) WE-08: the spec has not decided who may enter a weight manually
        //  (WeightMessages.WE_08_NO_MANUAL). Until then anyone who can record weights may.

        // WE-02: today's attendance for this worker, at a site the user can see. Locked so two
        // phones sending the worker's last allowed weighing are counted one after the other (WE-04)
        Attendance attendance = request.attendanceId() == null ? null
                : attendanceRepository.findVisibleByIdForUpdate(request.attendanceId())
                        .filter(found -> found.getEmployee().getEmployeeCode().equals(employeeCode))
                        .orElse(null);
        if (attendance == null) {
            throw new IllegalArgumentException(WeightMessages.WE_02_NO_ATTENDANCE);
        }
        LocalDate weighDate = attendance.getAttendanceDate();
        checkWeighedOnItsDate(request, weighDate);
        // WE-03
        if (!attendance.getWorkType().isWeighingRequired()) {
            throw new IllegalArgumentException(WeightMessages.WE_03_NOT_WEIGHING);
        }
        // WE-05
        Estate estate = attendance.getEstate();
        if (!Boolean.TRUE.equals(estate.getActive())) {
            throw new IllegalArgumentException(WeightMessages.WE_05_INACTIVE_SITE);
        }
        // WE-04: non-cancelled weighings at every site
        int limit = estate.getMaxDailyTransactions();
        if (weightRecordRepository.countActive(employeeCode, weighDate) >= limit) {
            throw new IllegalArgumentException(WeightMessages.limitReached(limit));
        }
        // WE-10: never reused, so cancelled weighings count too
        int entrySequence = weightRecordRepository.maxSequence(employeeCode, weighDate) + 1;

        BigDecimal variance = varianceFor(request, estate);
        WeightRecord record = new WeightRecord();
        record.setWeightId(request.weightId());
        record.setAttendance(attendance);
        record.setEmployee(attendance.getEmployee());
        record.setWeighDate(weighDate);
        record.setWeighedAt(request.weighedAt());
        record.setSupervisor(user);
        record.setEstateId(estate.getId());
        // Read-only mapping; set so the returned DTO has the estate without a reload
        record.setEstate(estate);
        record.setWorkType(attendance.getWorkType());
        record.setCollectionPoint(attendance.getCollectionPoint());
        record.setEntryType(request.entryType());
        record.setOriginalWeightKg(request.weightKg());
        record.setWeightKg(request.weightKg());
        record.setVariancePercent(variance);
        record.setWeightAfterVariance(WeightCalculator.afterVariance(request.weightKg(), variance));
        record.setEntrySequence(entrySequence);
        // The id is assigned, but the null version makes save() insert rather than merge
        return weightRecordRepository.saveAndFlush(record);
    }

    /**
     * TODO(decision CL-02): for a weighing sent later, use the variance the phone applied
     *  (request.variancePercent()) or the site's value when it arrives? Server value for now,
     *  so a phone can't send its own variance.
     */
    private BigDecimal varianceFor(RecordWeightRequest request, Estate estate) {
        return WeightCalculator.variance(estate.getVariancePercent());
    }

    // D-10: phone records are accepted later, but only for the day of their attendance
    private void checkWeighedOnItsDate(RecordWeightRequest request, LocalDate weighDate) {
        if (request.weighedAt() == null
                || weighDate.isAfter(LocalDate.now(clock))
                || request.weighedAt().isAfter(clock.instant().plus(MAX_CLOCK_SKEW))
                || !LocalDate.ofInstant(request.weighedAt(), clock.getZone()).equals(weighDate)) {
            throw new IllegalArgumentException(WeightMessages.TODAY_ONLY);
        }
    }

    private Map<String, WeightRecordRepository.DayCount> countByEmployee(LocalDate date, List<String> employeeCodes) {
        Map<String, WeightRecordRepository.DayCount> counts = new HashMap<>();
        for (int from = 0; from < employeeCodes.size(); from += LOOKUP_CHUNK_SIZE) {
            List<String> chunk = employeeCodes.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, employeeCodes.size()));
            for (WeightRecordRepository.DayCount count : weightRecordRepository.countByEmployee(date, chunk)) {
                counts.put(count.getEmployeeCode(), count);
            }
        }
        return counts;
    }
}
