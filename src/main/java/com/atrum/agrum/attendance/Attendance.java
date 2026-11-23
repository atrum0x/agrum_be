package com.atrum.agrum.attendance;

import com.atrum.agrum.division.Division;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.field.Field;
import com.atrum.agrum.security.EstateSecuredEntity;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.workType.WorkType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per worker per day. The working site is the estate_id,
 * so the estate filter limits attendance to the user's visible sites.
 */
@Entity
@Table(
        name = "attendance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_attendance_employee_day",
                        columnNames = {"employee_code", "attendance_date"}
                )
        },
        indexes = {
                @Index(name = "idx_attendance_date_estate", columnList = "attendance_date, estate_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Attendance extends EstateSecuredEntity {

    /**
     * Created on the phone (or by the server on web), so a resend is idempotent.
     * Not generated: save() treats the row as new while version is null.
     */
    @Id
    @Column(name = "attendance_id", nullable = false, updatable = false)
    private UUID attendanceId;

    // AT-11: today only. Worker and date can never be corrected
    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_code", nullable = false, updatable = false)
    private Employee employee;

    // Working site. Written through EstateSecuredEntity.estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    // Copied from the employee when recorded; null when the employee has no home estate
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_estate_id", updatable = false)
    private Estate homeEstate;

    // Must belong to the working site. The code is written here; the work type is looked up by
    // (site, code), so this mapping is read-only
    @Column(name = "work_type_code", nullable = false, length = 20)
    private String workTypeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "work_type_code", referencedColumnName = "work_type_code", insertable = false, updatable = false)
    })
    private WorkType workType;

    // AT-19: must belong to the working site; copied to weighings later. Read-only like the work type
    @Column(name = "division_code", nullable = false, length = 30)
    private String divisionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "division_code", referencedColumnName = "division_code", insertable = false, updatable = false)
    })
    private Division division;

    // AT-09: must belong to the division; copied to weighings later. Read-only like the work type
    @Column(name = "field_code", nullable = false, length = 30)
    private String fieldCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "field_code", referencedColumnName = "field_code", insertable = false, updatable = false)
    })
    private Field field;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_category", nullable = false, length = 20)
    private AttendanceCategory attendanceCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false, updatable = false)
    private AppUser recordedBy;

    // Submit time on the phone/web; can be earlier than receivedAt for offline records
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    // optimistic concurrency for corrections
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    public void setWorkType(WorkType workType) {
        this.workType = workType;
        this.workTypeCode = workType == null ? null : workType.getWorkTypeCode();
    }

    public void setDivision(Division division) {
        this.division = division;
        this.divisionCode = division == null ? null : division.getDivisionCode();
    }

    public void setField(Field field) {
        this.field = field;
        this.fieldCode = field == null ? null : field.getFieldCode();
    }
}
