package com.atrum.agrum.attendance;

import com.atrum.agrum.security.EstateSecuredEntity;
import com.atrum.agrum.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * History of every Correct / Delete made on an attendance row.
 * estate_id is the attendance's working site after the action (before it, for a Delete),
 * so the estate filter keeps the history visible after the attendance is deleted.
 */
@Entity
@Table(
        name = "attendance_corrections",
        indexes = {
                @Index(name = "idx_att_corr_attendance", columnList = "attendance_id, corrected_at"),
                // The 10 pm summary rerun finds the dates changed since its last run
                @Index(name = "idx_att_corr_time", columnList = "corrected_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AttendanceCorrection extends EstateSecuredEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain value, not a FK, so the history survives a Delete
    @Column(name = "attendance_id", nullable = false, updatable = false)
    private UUID attendanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10, updatable = false)
    private CorrectionAction action;

    // JSON snapshot of the corrected fields before the change
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String oldValues;

    // Null for Delete
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", updatable = false, columnDefinition = "jsonb")
    private String newValues;

    // mandatory, at most 300 characters
    @Column(name = "reason", nullable = false, length = 300, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by", nullable = false, updatable = false)
    private AppUser correctedBy;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;
}
