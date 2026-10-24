package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.DailyWeightSummaryDto;
import com.atrum.agrum.weight.dto.DailyWeightSummaryResultDto;
import com.atrum.agrum.weight.dto.DailyWeightSummarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily Weight Summary (FSD 2.9): rebuilding a date's stored rows, and the read-only screen.
 * The screen is limited to the user's visible sites by the estate filter (DS-02).
 */
@Service
@RequiredArgsConstructor
public class DailyWeightSummaryService {

    static final String JOB_NAME = "daily-weight-summary";

    private final DailyWeightSummaryRepository dailyWeightSummaryRepository;
    private final JobRunStateRepository jobRunStateRepository;
    private final UserService userService;
    private final Clock clock;

    /**
     * Replaces the date's rows: a worker whose only weighing was cancelled loses their row.
     * Uses the sites' current Full / Half Day standards (D-05). Totals add the rounded
     * weight-after-variance of each counted weighing (CL-01).
     */
    @Transactional
    public int rebuild(LocalDate date, Instant calculatedAt) {
        dailyWeightSummaryRepository.deleteForDate(date);
        List<DailyWeightSummary> rows = dailyWeightSummaryRepository.calculateTotals(date).stream()
                .map(total -> toSummary(date, total, calculatedAt))
                .toList();
        dailyWeightSummaryRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public DailyWeightSummaryResultDto search(DailyWeightSummarySearchRequest request) {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate dateFrom = request.dateFrom() != null ? request.dateFrom() : yesterday;
        LocalDate dateTo = request.dateTo() != null ? request.dateTo() : yesterday;
        // DS-01
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException(WeightMessages.DS_01_DATES);
        }
        List<DailyWeightSummaryDto> rows = dailyWeightSummaryRepository.search(dateFrom, dateTo,
                        term(request.employeeCode()), term(request.estateId()),
                        term(request.cpCode()), term(request.workType())).stream()
                .map(DailyWeightSummaryDto::from)
                .toList();
        Instant lastCalculatedAt = jobRunStateRepository.findById(JOB_NAME)
                .map(JobRunState::getLastSuccessAt)
                .orElse(null);
        return new DailyWeightSummaryResultDto(lastCalculatedAt, rows);
    }

    /** The same search, for the logged-in user's own rows (DS-02, TC-68); any worker filter is replaced. */
    @Transactional(readOnly = true)
    public DailyWeightSummaryResultDto searchMine(DailyWeightSummarySearchRequest request) {
        AppUser user = userService.getCurrentUser();
        if (user.getEmployee() == null) {
            throw new IllegalArgumentException("Your login is not linked to an employee.");
        }
        return search(new DailyWeightSummarySearchRequest(request.dateFrom(), request.dateTo(),
                user.getEmployee().getEmployeeCode(), request.estateId(), request.cpCode(), request.workType()));
    }

    private static DailyWeightSummary toSummary(LocalDate date, DailyWeightSummaryRepository.DailyTotal total,
                                                Instant calculatedAt) {
        WeightCalculator.DayResult day = WeightCalculator.classify(
                total.getDailyTotalKg(), total.getFullDayQtyKg(), total.getHalfDayQtyKg());
        DailyWeightSummary summary = new DailyWeightSummary();
        summary.setSummaryDate(date);
        summary.setEmployeeCode(total.getEmployeeCode());
        summary.setEstateId(total.getEstateId());
        summary.setCollectionPointCode(total.getCollectionPointCode());
        summary.setWorkType(total.getWorkType());
        summary.setAttendanceCategory(AttendanceCategory.valueOf(total.getAttendanceCategory()));
        summary.setTransactionCount(total.getTransactionCount().intValue());
        summary.setDailyTotalKg(total.getDailyTotalKg());
        summary.setDayClassification(day.classification());
        summary.setOverweightKg(day.overweightKg());
        summary.setCalculatedAt(calculatedAt);
        return summary;
    }

    private static String term(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
