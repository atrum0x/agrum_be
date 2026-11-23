package com.atrum.agrum.employee;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.division.Division;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.field.Field;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import com.atrum.agrum.user.AppUser;

@Entity
@Table(
        name = "employees",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_nic",
                        columnNames = "nic"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    /**
     * Official business/ERP employee number.
     * This remains the primary key for now.
     */
    @Id
    @Column(
            name = "employee_code",
            nullable = false,
            unique = true,
            updatable = false,
            length = 50
    )
    private String employeeCode;


    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "nic", unique = true, length = 30)
    private String nic;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "designation", nullable = true, length = 50)
    private String designation;

    // Employment Category; the default Attendance Category (FSD 2.4, 2.6)
    @Enumerated(EnumType.STRING)
    @Column(name = "employee_category", nullable = false, length = 10)
    private AttendanceCategory employeeCategory;

    @Column(name = "employment_status", nullable = false, length = 30)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Normal/current working site.
     * This is separate from AppUser.allowedEstates,
     * which represents login authorization.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_estate_id")
    private Estate defaultEstate;

    /**
     * Normal division; must belong to defaultEstate (PB-16). The code is written here; the division
     * is looked up by (default site, code), so this mapping is read-only.
     */
    @Column(name = "default_division_code", length = 30)
    private String defaultDivisionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "default_estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "default_division_code", referencedColumnName = "division_code", insertable = false, updatable = false)
    })
    private Division defaultDivision;

    public void setDefaultDivision(Division division) {
        this.defaultDivision = division;
        this.defaultDivisionCode = division == null ? null : division.getDivisionCode();
    }

    /**
     * Normal field; must belong to defaultDivision (PB-11). The code is written here; the field is
     * looked up by (default site, code), so this mapping is read-only.
     */
    @Column(name = "default_field_code")
    private String defaultFieldCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "default_estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "default_field_code", referencedColumnName = "field_code", insertable = false, updatable = false)
    })
    private Field defaultField;

    public void setDefaultField(Field field) {
        this.defaultField = field;
        this.defaultFieldCode = field == null ? null : field.getFieldCode();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_estate_id")
    private Estate homeEstate;

    @OneToOne(mappedBy = "employee", fetch = FetchType.LAZY)
    private AppUser userAccount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public String getFullName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();

        return (first + " " + last).trim();
    }
}