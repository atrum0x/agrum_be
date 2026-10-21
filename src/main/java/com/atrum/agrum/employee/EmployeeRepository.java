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
