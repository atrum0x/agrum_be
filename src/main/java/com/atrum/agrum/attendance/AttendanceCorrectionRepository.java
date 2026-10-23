package com.atrum.agrum.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    List<AttendanceCorrection> findAllByAttendanceIdOrderByCorrectedAtDesc(UUID attendanceId);

    // Latest correction of each attendance, for the list's "Last Corrected By / At" columns
    @Query("""
            SELECT c FROM AttendanceCorrection c
            WHERE c.attendanceId IN :attendanceIds
              AND c.correctedAt = (
                  SELECT MAX(c2.correctedAt) FROM AttendanceCorrection c2
                  WHERE c2.attendanceId = c.attendanceId
              )
            """)
    List<AttendanceCorrection> findLatestFor(@Param("attendanceIds") Collection<UUID> attendanceIds);
}
