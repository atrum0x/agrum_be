package com.atrum.agrum.employee.dto;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.employee.EmploymentStatus;

import java.time.LocalDate;

public record UpdateEmployeeRequest(
        String firstName,
        String lastName,
        String nic,
        String phoneNumber,
        String bankAccountNumber,
        String designation,
        AttendanceCategory employeeCategory,
        EmploymentStatus employmentStatus,
        LocalDate startDate,
        LocalDate endDate,
        String defaultEstateId,
        String defaultCollectionPointCode,
        String homeEstateId
) {
}
