package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.Attendance;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.security.EstateSecuredEntity;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.workType.WorkType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per weighing. The working site is the estate_id (copied from the attendance),
 * so the estate filter limits weighings to the user's visible sites.
 */
@Entity
@Table(
        name = "weight_records",
        uniqueConstraints = {
                // WE-10: an entry sequence is never reused for a worker on a day
                @UniqueConstraint(
                        name = "uk_weight_employee_day_seq",
                        columnNames = {"employee_code", "weigh_date", "entry_sequence"}
                )
        },
        indexes = {
                @Index(name = "idx_weight_date_estate", columnList = "weigh_date, estate_id"),
                @Index(name = "idx_weight_employee_date", columnList = "employee_code, weigh_date"),
                @Index(name = "idx_weight_attendance", columnList = "attendance_id"),
                @Index(name = "idx_weight_approval_status", columnList = "approval_status"),
                // The 10 pm summary rerun finds the dates received since its last run
                @Index(name = "idx_weight_received", columnList = "received_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class WeightRecord extends EstateSecuredEntity {

    /**
     * Created on the phone, so a resend is idempotent (WE-09).
     * Not generated: save() treats the row as new while version is null.
     */
    @Id
    @Column(name = "weight_id", nullable = false, updatable = false)
    private UUID weightId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false, updatable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_code", nullable = false, updatable = false)
    private Employee employee;

    // = attendance date; used for the daily limit, numbering and the summary
    @Column(name = "weigh_date", nullable = false, updatable = false)
    private LocalDate weighDate;

    // Phone time at save; can be earlier than receivedAt for offline records
    @Column(name = "weighed_at", nullable = false, updatable = false)
    private Instant weighedAt;

    // Always the logged-in user who saved the weighing
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor", nullable = false, updatable = false)
    private AppUser supervisor;

    // Working site. Written through EstateSecuredEntity.estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    // Copied from the attendance; an attendance correction copies the new value here
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_type", nullable = false)
    private WorkType workType;

    // Copied from the attendance; an attendance correction copies the new value here
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_point_code", nullable = false)
    private CollectionPoint collectionPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    // The weight as first recorded; never changes
    @Column(name = "original_weight_kg", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal originalWeightKg;

    // The weight in use; changed only by an approved correction
    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightKg;

    // Snapshot of the site variance at weighing time; later site changes never recalculate
    @Column(name = "variance_percent", nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal variancePercent;

    // weightKg x (1 + variancePercent / 100), 2 dp
    @Column(name = "weight_after_variance", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightAfterVariance;

    // Assigned by the server: highest for the worker and day (incl. cancelled) + 1
    @Column(name = "entry_sequence", nullable = false, updatable = false)
    private Integer entrySequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_state", nullable = false, length = 10)
    private LineState lineState = LineState.RECORDED;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 25)
    private ApprovalStatus approvalStatus = ApprovalStatus.RECORDED;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    // optimistic concurrency for approvals (WH-10)
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}
