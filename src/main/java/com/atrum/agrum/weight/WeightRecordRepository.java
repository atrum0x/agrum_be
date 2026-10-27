package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeightRecordRepository extends JpaRepository<WeightRecord, UUID> {

    // JPQL, so the estate filter limits this to the user's visible sites
    @Query("SELECT w FROM WeightRecord w WHERE w.weightId = :weightId")
    Optional<WeightRecord> findVisibleById(@Param("weightId") UUID weightId);

    // Locked so a change request and an approval on the same weighing can't overlap (WH-05)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WeightRecord w WHERE w.weightId = :weightId")
    Optional<WeightRecord> findVisibleByIdForUpdate(@Param("weightId") UUID weightId);

    // Native so the estate filter does not apply: WE-04 counts weighings at every site
    @Query(value = """
            SELECT COUNT(*) FROM weight_records
            WHERE employee_code = :employeeCode AND weigh_date = :date AND line_state = 'RECORDED'
            """, nativeQuery = true)
    long countActive(@Param("employeeCode") String employeeCode, @Param("date") LocalDate date);

    // Native for the same reason; includes cancelled so a number is never reused (WE-10)
    @Query(value = """
            SELECT COALESCE(MAX(entry_sequence), 0) FROM weight_records
            WHERE employee_code = :employeeCode AND weigh_date = :date
            """, nativeQuery = true)
    int maxSequence(@Param("employeeCode") String employeeCode, @Param("date") LocalDate date);

    interface DayCount {
        String getEmployeeCode();
        Long getActiveCount();
        Integer getLastSequence();
    }

    // Native for the same reason: "Entries Today" and the provisional number for the worker picker
    @Query(value = """
            SELECT employee_code                                     AS "employeeCode",
                   COUNT(*) FILTER (WHERE line_state = 'RECORDED')   AS "activeCount",
                   MAX(entry_sequence)                               AS "lastSequence"
            FROM weight_records
            WHERE weigh_date = :date AND employee_code IN (:employeeCodes)
            GROUP BY employee_code
            """, nativeQuery = true)
    List<DayCount> countByEmployee(@Param("date") LocalDate date,
                                   @Param("employeeCodes") Collection<String> employeeCodes);

    // Native so an attendance correction sees weighings the admin may not see (AT-16)
    @Query(value = """
            SELECT COUNT(*) FROM weight_records
            WHERE attendance_id = :attendanceId AND line_state = 'RECORDED'
            """, nativeQuery = true)
    long countActiveByAttendance(@Param("attendanceId") UUID attendanceId);

    // Native for the same reason; includes cancelled ones, which still reference the attendance (AT-17)
    @Query(value = "SELECT COUNT(*) FROM weight_records WHERE attendance_id = :attendanceId", nativeQuery = true)
    long countByAttendance(@Param("attendanceId") UUID attendanceId);

    /**
     * Attendance correction cascade: the weighings follow the corrected site, collection point
     * and work type. Weights are unchanged. Bumps row_version so an open approval sees the change.
     * Doesn't clear the persistence context: the correction goes on using the loaded attendance.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE weight_records
            SET estate_id = :estateId, collection_point_code = :cpCode, work_type = :workType,
                row_version = row_version + 1
            WHERE attendance_id = :attendanceId
            """, nativeQuery = true)
    int copyFromAttendance(@Param("attendanceId") UUID attendanceId,
                           @Param("estateId") String estateId,
                           @Param("cpCode") String cpCode,
                           @Param("workType") String workType);

    /**
     * Weight History search (WH-01, WH-02). Blank strings mean "no filter"; the enum filters
     * take the allowed values, so the service passes every value when one is not filtered.
     * Pass Pageable.unpaged() for the export.
     */
    @Query(value = """
            SELECT w FROM WeightRecord w
            JOIN FETCH w.employee e
            JOIN FETCH w.attendance a
            JOIN FETCH w.supervisor s
            LEFT JOIN FETCH s.employee
            JOIN FETCH w.estate
            JOIN FETCH w.collectionPoint
            JOIN FETCH w.workType
            WHERE w.weighDate BETWEEN :dateFrom AND :dateTo
              AND (:employeeCode = '' OR e.employeeCode = :employeeCode)
              AND (:supervisor = '' OR s.username = :supervisor)
              AND (:estateId = '' OR w.estateId = :estateId)
              AND (:cpCode = '' OR w.cpCode = :cpCode)
              AND (:workType = '' OR w.workTypeCode = :workType)
              AND a.attendanceCategory IN :categories
              AND w.entryType IN :entryTypes
              AND w.lineState IN :lineStates
              AND w.approvalStatus IN :approvalStatuses
            ORDER BY w.weighedAt, e.employeeCode, w.entrySequence
            """,
            countQuery = """
            SELECT COUNT(w) FROM WeightRecord w
            JOIN w.employee e
            JOIN w.attendance a
            JOIN w.supervisor s
            WHERE w.weighDate BETWEEN :dateFrom AND :dateTo
              AND (:employeeCode = '' OR e.employeeCode = :employeeCode)
              AND (:supervisor = '' OR s.username = :supervisor)
              AND (:estateId = '' OR w.estateId = :estateId)
              AND (:cpCode = '' OR w.cpCode = :cpCode)
              AND (:workType = '' OR w.workTypeCode = :workType)
              AND a.attendanceCategory IN :categories
              AND w.entryType IN :entryTypes
              AND w.lineState IN :lineStates
              AND w.approvalStatus IN :approvalStatuses
            """)
    Page<WeightRecord> search(@Param("dateFrom") LocalDate dateFrom,
                              @Param("dateTo") LocalDate dateTo,
                              @Param("employeeCode") String employeeCode,
                              @Param("supervisor") String supervisor,
                              @Param("estateId") String estateId,
                              @Param("cpCode") String cpCode,
                              @Param("workType") String workType,
                              @Param("categories") Collection<AttendanceCategory> categories,
                              @Param("entryTypes") Collection<EntryType> entryTypes,
                              @Param("lineStates") Collection<LineState> lineStates,
                              @Param("approvalStatuses") Collection<ApprovalStatus> approvalStatuses,
                              Pageable pageable);

    interface DayTotals {
        long getWeighings();
        BigDecimal getTotalKg();
    }

    // JPQL, so the estate filter limits it to the user's sites; cancelled weighings excluded (Dashboard, FSD 2.2)
    @Query("""
            SELECT COUNT(w) AS weighings, COALESCE(SUM(w.weightAfterVariance), 0) AS totalKg
            FROM WeightRecord w
            WHERE w.weighDate = :date AND w.lineState = com.atrum.agrum.weight.LineState.RECORDED
              AND (:employeeCode = '' OR w.employee.employeeCode = :employeeCode)
            """)
    DayTotals totalsOn(@Param("date") LocalDate date, @Param("employeeCode") String employeeCode);
}
