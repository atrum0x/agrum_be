package com.atrum.agrum.employee;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    boolean existsByNic(String nic);
}
