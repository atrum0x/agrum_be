package com.atrum.agrum.weight.dto;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.weight.ApprovalStatus;
import com.atrum.agrum.weight.EntryType;
import com.atrum.agrum.weight.LineState;

import java.time.LocalDate;

/**
 * Weight History filters. Dates default to today; blank or null filters are ignored.
 */
public record WeightHistorySearchRequest(
        LocalDate dateFrom,
        LocalDate dateTo,
        String employeeCode,
        // Supervisor's username
        String supervisor,
        String estateId,
        String cpCode,
        String workType,
        // "Employment Category": the attendance category of the day (D-18)
        AttendanceCategory attendanceCategory,
        EntryType entryType,
        LineState lineState,
        ApprovalStatus approvalStatus
) {}
