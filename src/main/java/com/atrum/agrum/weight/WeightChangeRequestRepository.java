package com.atrum.agrum.weight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeightChangeRequestRepository extends JpaRepository<WeightChangeRequest, Long> {

    // JPQL, so the estate filter limits this to the user's visible sites
    @Query("""
            SELECT r FROM WeightChangeRequest r
            JOIN FETCH r.weight
            WHERE r.id = :id
            """)
    Optional<WeightChangeRequest> findVisibleById(@Param("id") Long id);

    // JPQL, so a request at an invisible site is not found (A11)
    @Query("SELECT r.weight.weightId FROM WeightChangeRequest r WHERE r.id = :id")
    Optional<UUID> findWeightIdById(@Param("id") Long id);

    // View History: every request on a weighing, newest first
    @Query("""
            SELECT r FROM WeightChangeRequest r
            JOIN FETCH r.requestedBy
            LEFT JOIN FETCH r.decidedBy
            WHERE r.weight.weightId = :weightId
            ORDER BY r.id DESC
            """)
    List<WeightChangeRequest> findHistory(@Param("weightId") UUID weightId);

    // WH-05 check before insert; the partial unique index backs it up
    boolean existsByWeightWeightIdAndRequestStatus(UUID weightId, ApprovalStatus requestStatus);

    // Latest request of each weighing, for the history's Requested / Approved columns
    @Query("""
            SELECT r FROM WeightChangeRequest r
            JOIN FETCH r.requestedBy
            LEFT JOIN FETCH r.decidedBy
            WHERE r.weight.weightId IN :weightIds
              AND r.id = (
                  SELECT MAX(r2.id) FROM WeightChangeRequest r2
                  WHERE r2.weight.weightId = r.weight.weightId
              )
            """)
    List<WeightChangeRequest> findLatestFor(@Param("weightIds") Collection<UUID> weightIds);

    // Approver queue, oldest first; visible sites only
    @Query("""
            SELECT r FROM WeightChangeRequest r
            JOIN FETCH r.weight w
            JOIN FETCH w.employee
            JOIN FETCH w.estate
            JOIN FETCH r.requestedBy
            WHERE r.requestStatus = com.atrum.agrum.weight.ApprovalStatus.PENDING_AUTHORIZATION
            ORDER BY r.id
            """)
    List<WeightChangeRequest> findPending();

    // Dashboard pending count; visible sites only
    @Query("""
            SELECT COUNT(r) FROM WeightChangeRequest r
            WHERE r.requestStatus = com.atrum.agrum.weight.ApprovalStatus.PENDING_AUTHORIZATION
            """)
    long countPending();
}
