package com.atrum.agrum.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Today's live counts (FSD 2.2), not the 10 pm summary. attendanceToday is null on the
 * picker's own dashboard, which shows only their weighings.
 */
public record DashboardDto(
        LocalDate today,
        Instant calculatedAt,
        Long attendanceToday,
        long weighingsToday,
        BigDecimal totalWeightTodayKg
) {}
