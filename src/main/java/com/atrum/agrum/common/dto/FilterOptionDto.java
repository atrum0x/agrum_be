package com.atrum.agrum.common.dto;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.user.AppUser;
import org.springframework.util.StringUtils;

/**
 * One suggestion for a search filter (worker, supervisor, recorder): the code to filter by and
 * the person's name. Only these two fields, so a user who may search a screen gets suggestions
 * without being granted the full employee or user lists.
 */
public record FilterOptionDto(String value, String description) {

    /** How many worker suggestions a filter-options call returns; the user types to narrow them. */
    public static final int WORKER_LIMIT = 20;

    public static FilterOptionDto of(Employee employee) {
        return new FilterOptionDto(employee.getEmployeeCode(), blankToNull(employee.getFullName()));
    }

    public static FilterOptionDto of(AppUser user) {
        Employee employee = user.getEmployee();
        return new FilterOptionDto(user.getUsername(), employee == null ? null : blankToNull(employee.getFullName()));
    }

    /** "" means "no search", the convention the JPQL searches use. */
    public static String searchTerm(String search) {
        return StringUtils.hasText(search) ? search.trim() : "";
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
