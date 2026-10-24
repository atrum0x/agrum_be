package com.atrum.agrum.weight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyWeightSummaryRepository extends JpaRepository<DailyWeightSummary, DailyWeightSummary.Key> {

    /**
     * One worker's totals for a day, as the 10 pm job needs them. Non-cancelled weighings
     * of weighing-required work types only; site, collection point, work type and category
     * come from the day's attendance; standards are the site's current ones (D-05).
     */
    interface DailyTotal {
        String getEmployeeCode();
        String getEstateId();
        String getCollectionPointCode();
        String getWorkType();
        String getAttendanceCategory();
        Long getTransactionCount();
        BigDecimal getDailyTotalKg();
        BigDecimal getFullDayQtyKg();
        BigDecimal getHalfDayQtyKg();
    }

    // Native: the job has no logged-in user and must see every site
    @Query(value = """
            SELECT w.employee_code              AS "employeeCode",
                   a.estate_id                  AS "estateId",
                   a.collection_point_code      AS "collectionPointCode",
                   a.work_type                  AS "workType",
                   a.attendance_category        AS "attendanceCategory",
                   COUNT(*)                     AS "transactionCount",
                   SUM(w.weight_after_variance) AS "dailyTotalKg",
                   e.full_day_qty_kg            AS "fullDayQtyKg",
                   e.half_day_qty_kg            AS "halfDayQtyKg"
            FROM weight_records w
            JOIN attendance a ON a.attendance_id = w.attendance_id
            JOIN work_types wt ON wt.work_type = a.work_type
            JOIN estates e ON e.id = a.estate_id
            WHERE w.weigh_date = :date
              AND w.line_state = 'RECORDED'
              AND wt.weighing_required = TRUE
            GROUP BY w.employee_code, a.estate_id, a.collection_point_code, a.work_type,
                     a.attendance_category, e.full_day_qty_kg, e.half_day_qty_kg
            """, nativeQuery = true)
    List<DailyTotal> calculateTotals(@Param("date") LocalDate date);

    // Native: bulk deletes ignore the estate filter anyway; the job replaces the whole date
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM daily_weight_summaries WHERE summary_date = :date", nativeQuery = true)
    int deleteForDate(@Param("date") LocalDate date);

    /**
     * Earlier dates changed since the last successful run (D-23): weighings received late,
     * change requests decided, and attendance corrected or deleted. A deleted attendance
     * survives only in the correction history, so the date comes from its old_values JSON.
     */
    @Query(value = """
            SELECT weigh_date FROM weight_records WHERE received_at > :since
            UNION
            SELECT w.weigh_date FROM weight_change_requests r
            JOIN weight_records w ON w.weight_id = r.weight_id
            WHERE r.decided_at > :since
            UNION
            SELECT CAST(c.old_values ->> 'attendanceDate' AS DATE) FROM attendance_corrections c
            WHERE c.corrected_at > :since
            """, nativeQuery = true)
    List<LocalDate> findDatesChangedSince(@Param("since") Instant since);

    // JPQL, so the estate filter limits this to the user's visible sites (DS-02)
    @Query("""
            SELECT s FROM DailyWeightSummary s
            JOIN FETCH s.employee e
            JOIN FETCH s.estate
            WHERE s.summaryDate BETWEEN :dateFrom AND :dateTo
              AND (:employeeCode = '' OR s.employeeCode = :employeeCode)
              AND (:estateId = '' OR s.estateId = :estateId)
              AND (:cpCode = '' OR s.collectionPointCode = :cpCode)
              AND (:workType = '' OR s.workType = :workType)
            ORDER BY s.summaryDate, s.estateId, s.employeeCode
            """)
    List<DailyWeightSummary> search(@Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    @Param("employeeCode") String employeeCode,
                                    @Param("estateId") String estateId,
                                    @Param("cpCode") String cpCode,
                                    @Param("workType") String workType);
}
