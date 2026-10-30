package com.atrum.agrum.face;

import com.atrum.agrum.employee.EmploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, String> {

    /** Templates of the workers the phone keeps in its site data (employed on the date, all sites). */
    @Query("""
            SELECT f FROM FaceTemplate f
            WHERE f.employeeCode IN (
                SELECT e.employeeCode FROM Employee e
                WHERE e.employmentStatus = :status
                  AND (e.startDate IS NULL OR e.startDate <= :date)
                  AND (e.endDate IS NULL OR e.endDate >= :date)
            )
            ORDER BY f.employeeCode
            """)
    List<FaceTemplate> findForEmployedOn(@Param("status") EmploymentStatus status, @Param("date") LocalDate date);
}
