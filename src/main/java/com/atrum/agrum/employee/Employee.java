package com.atrum.agrum.employee;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.collectionPoint.CollectionPoint;
import com.atrum.agrum.estate.Estate;
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
     * Normal collection point; must belong to defaultEstate (PB-11). The code is written here; the
     * collection point is looked up by (default site, code), so this mapping is read-only.
     */
    @Column(name = "default_collection_point_code")
    private String defaultCollectionPointCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "default_estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "default_collection_point_code", referencedColumnName = "cp_code", insertable = false, updatable = false)
    })
    private CollectionPoint defaultCollectionPoint;

    public void setDefaultCollectionPoint(CollectionPoint collectionPoint) {
        this.defaultCollectionPoint = collectionPoint;
        this.defaultCollectionPointCode = collectionPoint == null ? null : collectionPoint.getCpCode();
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