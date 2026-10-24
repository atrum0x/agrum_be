package com.atrum.agrum.dashboard;

import com.atrum.agrum.attendance.AttendanceRepository;
import com.atrum.agrum.dashboard.dto.DashboardDto;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Dashboard counts (FSD 2.2) for the sites the user can see (estate filter). Pending
 * Authorizations comes from GET /api/weight-changes/pending/count, granted to approvers only.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AttendanceRepository attendanceRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardDto getDashboard() {
        LocalDate today = LocalDate.now(clock);
        WeightRecordRepository.DayTotals totals = weightRecordRepository.totalsOn(today, "");
        return new DashboardDto(today, clock.instant(), attendanceRepository.countByAttendanceDate(today),
                totals.getWeighings(), totals.getTotalKg().setScale(2, RoundingMode.HALF_UP));
    }

    /** Pickers: only their own weighings and total for today; attendance is hidden. */
    @Transactional(readOnly = true)
    public DashboardDto getMyDashboard() {
        AppUser user = userService.getCurrentUser();
        if (user.getEmployee() == null) {
            throw new IllegalArgumentException("Your login is not linked to an employee.");
        }
        LocalDate today = LocalDate.now(clock);
        WeightRecordRepository.DayTotals totals =
                weightRecordRepository.totalsOn(today, user.getEmployee().getEmployeeCode());
        return new DashboardDto(today, clock.instant(), null,
                totals.getWeighings(), totals.getTotalKg().setScale(2, RoundingMode.HALF_UP));
    }
}
