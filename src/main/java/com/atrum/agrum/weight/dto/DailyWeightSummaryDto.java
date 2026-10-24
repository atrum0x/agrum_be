package com.atrum.agrum.weight.dto;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.weight.DailyWeightSummary;
import com.atrum.agrum.weight.DayClassification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyWeightSummaryDto(
        LocalDate summaryDate,
        String employeeCode,
        String employeeName,
        // "Employment Category": the attendance category of the day (D-18)
        AttendanceCategory attendanceCategory,
        String estateId,
        String estateName,
        String cpCode,
        String workType,
        int transactionCount,
        BigDecimal dailyTotalKg,
        DayClassification dayClassification,
        BigDecimal overweightKg,
        // "Last Updated"
        Instant calculatedAt
) {
    public static DailyWeightSummaryDto from(DailyWeightSummary summary) {
        return new DailyWeightSummaryDto(
                summary.getSummaryDate(),
                summary.getEmployeeCode(),
                summary.getEmployee().getFullName(),
                summary.getAttendanceCategory(),
                summary.getEstateId(),
                summary.getEstate().getName(),
                summary.getCollectionPointCode(),
                summary.getWorkType(),
                summary.getTransactionCount(),
                summary.getDailyTotalKg(),
                summary.getDayClassification(),
                summary.getOverweightKg(),
                summary.getCalculatedAt());
    }
}
