package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.security.EstateSecuredEntity;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Stored result of the 10 pm job: one row per worker per day, weighing-required work types only.
 * Rows for a date are deleted and inserted again on each run, never edited.
 * estate_id is the attendance's working site, so the estate filter applies.
 */
@Entity
@Table(
        name = "daily_weight_summaries",
        indexes = {
                @Index(name = "idx_dws_date_estate", columnList = "summary_date, estate_id")
        }
)
@IdClass(DailyWeightSummary.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class DailyWeightSummary extends EstateSecuredEntity {

    @Id
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Id
    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_code", insertable = false, updatable = false)
    private Employee employee;

    // Working site. Written through EstateSecuredEntity.estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    // Plain codes, copied from the attendance at run time
    @Column(name = "collection_point_code", nullable = false)
    private String collectionPointCode;

    @Column(name = "work_type", nullable = false, length = 50)
    private String workType;

    // D-18: the attendance category of the day
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_category", nullable = false, length = 20)
    private AttendanceCategory attendanceCategory;

    // Non-cancelled weighings
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;

    // Sum of the rounded weight-after-variance of the counted weighings
    @Column(name = "daily_total_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyTotalKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_classification", nullable = false, length = 10)
    private DayClassification dayClassification;

    @Column(name = "overweight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal overweightKg;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private LocalDate summaryDate;
        private String employeeCode;
    }
}
