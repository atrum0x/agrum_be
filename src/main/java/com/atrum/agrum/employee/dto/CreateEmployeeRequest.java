package com.atrum.agrum.employee.dto;

import com.atrum.agrum.employee.EmploymentStatus;

import java.time.LocalDate;

public record CreateEmployeeRequest(
        String employeeCode,
        String firstName,
        String lastName,
        String nic,
        String phoneNumber,
        String bankAccountNumber,
        String designation,
        String employeeCategory,
        EmploymentStatus employmentStatus,
        LocalDate startDate,
        LocalDate endDate,
        String defaultEstateId,
        String homeEstateId,
        String division,
        String field,
        boolean isUserAccount,
        String password
) {
}
