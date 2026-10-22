package com.atrum.agrum.employee;

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

    @Column(name = "employee_category", nullable = false)
    private String employeeCategory;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_estate_id")
    private Estate homeEstate;

    @OneToOne(mappedBy = "employee", fetch = FetchType.LAZY)
    private AppUser userAccount;

    @Column(name  = "division")
    private String division;

    @Column(name = "field")
    private String field;

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