package com.atrum.agrum.mobile.dto;

import com.atrum.agrum.attendance.dto.AttendanceWorkerDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the phone keeps for working offline (FSD 5.4), in one call. The phone refreshes it at
 * login and after each successful send.
 */
public record MobileSetupDto(
        LocalDate today,
        // The phone keeps its offset from this to check "today only" offline (D-10, AT-11)
        Instant serverTime,
        // The logged-in supervisor's default site; null if they have none
        String defaultEstateId,
        // Active sites the user can see
        List<MobileEstateDto> estates,
        List<MobileWorkTypeDto> workTypes,
        List<MobileCollectionPointDto> collectionPoints,
        // Active workers employed today, from all sites (attendance can be for other-site workers)
        List<AttendanceWorkerDto> workers,
        // Workers with attendance today on any site, so the phone can hide them (AT-02)
        List<String> attendedToday
) {}
