package com.atrum.agrum.employee;

import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.permission.PermissionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, String> {
    boolean existsByNic(String nic);

    @Query("""
            SELECT DISTINCT ps FROM Employee e
            JOIN e.userAccount u
            JOIN u.permissionSets ps
            WHERE e.employeeCode = :employeeCode
              AND (
                  :search = ''
                  OR LOWER(ps.id) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(ps.description) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    List<PermissionSet> findGrantedPermissionSets(
            @Param("employeeCode") String employeeCode,
            @Param("search") String search
    );

    @Query("""
            SELECT DISTINCT ps FROM PermissionSet ps
            WHERE NOT EXISTS (
                SELECT 1 FROM Employee e
                JOIN e.userAccount u
                JOIN u.permissionSets assigned
                WHERE e.employeeCode = :employeeCode
                  AND assigned.id = ps.id
            )
            AND (
                :search = ''
                OR LOWER(ps.id) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(ps.description) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """)
    List<PermissionSet> findUnassignedPermissionSets(
            @Param("employeeCode") String employeeCode,
            @Param("search") String search
    );

    @Query("""
            SELECT DISTINCT estate FROM Employee e
            JOIN e.userAccount u
            JOIN u.allowedEstates estate
            WHERE e.employeeCode = :employeeCode
              AND (
                  :search = ''
                  OR LOWER(estate.id) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(estate.name) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    List<Estate> findGrantedEstates(
            @Param("employeeCode") String employeeCode,
            @Param("search") String search
    );

    @Query("""
            SELECT DISTINCT estate FROM Estate estate
            WHERE NOT EXISTS (
                SELECT 1 FROM Employee e
                JOIN e.userAccount u
                JOIN u.allowedEstates assigned
                WHERE e.employeeCode = :employeeCode
                  AND assigned.id = estate.id
            )
            AND (
                :search = ''
                OR LOWER(estate.id) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(estate.name) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """)
    List<Estate> findUnassignedEstates(
            @Param("employeeCode") String employeeCode,
            @Param("search") String search
    );

    @Query("""
            SELECT DISTINCT e FROM Employee e
            JOIN e.userAccount u
            JOIN u.allowedEstates estate
            WHERE estate.id = :estateId
              AND (
                  :search = ''
                  OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY e.employeeCode
            """)
    List<Employee> findUsersGrantedEstate(
            @Param("estateId") String estateId,
            @Param("search") String search
    );

    /** Employees with a user account that have not been granted the estate. */
    @Query("""
            SELECT e FROM Employee e
            JOIN e.userAccount u
            WHERE NOT EXISTS (
                SELECT 1 FROM AppUser granted
                JOIN granted.allowedEstates assigned
                WHERE granted = u
                  AND assigned.id = :estateId
            )
            AND (
                :search = ''
                OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            ORDER BY e.employeeCode
            """)
    List<Employee> findUsersNotGrantedEstate(
            @Param("estateId") String estateId,
            @Param("search") String search
    );

    @Query("""
            SELECT e FROM Employee e
            WHERE :search IS NULL
               OR :search = '' 
               OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.nic) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    List<Employee> searchEmployees(@Param("search") String search);
}
