package com.atrum.agrum.attendance.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the record screen pre-fills, and the server clock the phone checks its date against (AT-11).
 */
public record AttendanceDefaultsDto(
        LocalDate today,
        Instant serverTime,
        // The logged-in supervisor's default site; null if they have none
        String defaultEstateId
) {}
