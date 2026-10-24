package com.atrum.agrum.weight;

import com.atrum.agrum.security.EstateSecuredEntity;
import com.atrum.agrum.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A correction or cancellation of a weighing, waiting for or given a decision.
 * The weighing changes only when the request is approved.
 * estate_id is copied from the weighing, so requests follow the same site visibility.
 * At most one PENDING_AUTHORIZATION request per weighing (WH-05), enforced by the
 * partial unique index in WeightSchemaInitializer.
 */
@Entity
@Table(
        name = "weight_change_requests",
        indexes = {
                @Index(name = "idx_wcr_weight", columnList = "weight_id, requested_at"),
                @Index(name = "idx_wcr_status", columnList = "request_status"),
                // The 10 pm summary rerun finds the dates changed since its last run
                @Index(name = "idx_wcr_decided", columnList = "decided_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class WeightChangeRequest extends EstateSecuredEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weight_id", nullable = false, updatable = false)
    private WeightRecord weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 15, updatable = false)
    private ChangeType changeType;

    // Weight in use when the request was made
    @Column(name = "previous_weight_kg", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal previousWeightKg;

    // Required for a correction; null for a cancellation
    @Column(name = "proposed_weight_kg", precision = 10, scale = 2, updatable = false)
    private BigDecimal proposedWeightKg;

    // mandatory, at most 300 characters (WH-07)
    @Column(name = "comment", nullable = false, length = 300, updatable = false)
    private String comment;

    // PENDING_AUTHORIZATION -> APPROVED / DISAPPROVED (never RECORDED)
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 25)
    private ApprovalStatus requestStatus = ApprovalStatus.PENDING_AUTHORIZATION;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private AppUser requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    // Approver or disapprover; may be the requester (D-08)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
