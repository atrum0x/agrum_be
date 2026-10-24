package com.atrum.agrum.attendance.dto;

import java.util.List;

/**
 * One batch from the web: one site, work type and collection point for all ticked workers.
 */
public record RecordAttendanceRequest(
        String estateId,
        String workType,
        String cpCode,
        List<AttendanceWorkerRequest> workers
) {}
