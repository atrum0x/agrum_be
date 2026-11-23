package com.atrum.agrum.attendance.dto;

import java.util.List;

/**
 * One batch from the web: one site, work type, division and field for all ticked workers.
 */
public record RecordAttendanceRequest(
        String estateId,
        String workType,
        String divisionCode,
        String fieldCode,
        List<AttendanceWorkerRequest> workers
) {}
