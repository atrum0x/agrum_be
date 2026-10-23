package com.atrum.agrum.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    // Native so the estate filter does not apply: AT-02 must see attendance recorded at every site
    @Query(value = """
            SELECT employee_code FROM attendance
            WHERE attendance_date = :date AND employee_code IN (:employeeCodes)
            """, nativeQuery = true)
    List<String> findEmployeeCodesRecordedOn(@Param("date") LocalDate date,
                                             @Param("employeeCodes") List<String> employeeCodes);

    // Native for the same reason: the worker picker hides workers marked at any site
    @Query(value = "SELECT employee_code FROM attendance WHERE attendance_date = :date", nativeQuery = true)
    List<String> findAllEmployeeCodesRecordedOn(@Param("date") LocalDate date);

    // JPQL, so the estate filter limits this to the user's visible sites
    @Query("SELECT a FROM Attendance a WHERE a.attendanceId = :attendanceId")
    Optional<Attendance> findVisibleById(@Param("attendanceId") UUID attendanceId);

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            JOIN FETCH a.estate
            JOIN FETCH a.workType
            JOIN FETCH a.collectionPoint
            JOIN FETCH a.recordedBy u
            WHERE a.attendanceDate BETWEEN :dateFrom AND :dateTo
              AND (:estateId = '' OR a.estateId = :estateId)
              AND (:cpCode = '' OR a.collectionPoint.cpCode = :cpCode)
              AND (:workType = '' OR a.workType.workType = :workType)
              AND (:recordedBy = '' OR u.username = :recordedBy)
              AND (
                  :employee = ''
                  OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :employee, '%'))
                  OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :employee, '%'))
                  OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :employee, '%'))
              )
            ORDER BY a.attendanceDate, a.estateId, e.employeeCode
            """)
    List<Attendance> search(@Param("dateFrom") LocalDate dateFrom,
                            @Param("dateTo") LocalDate dateTo,
                            @Param("employee") String employee,
                            @Param("estateId") String estateId,
                            @Param("cpCode") String cpCode,
                            @Param("workType") String workType,
                            @Param("recordedBy") String recordedBy);
}
