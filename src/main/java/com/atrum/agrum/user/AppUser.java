package com.atrum.agrum.user;

import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.permission.PermissionSet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_code",
            referencedColumnName = "employee_code",
            unique = true
    )
    private Employee employee;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_grants",
            joinColumns = @JoinColumn(name = "username"),
            inverseJoinColumns = @JoinColumn(name = "permission_set_id")
    )
    private Set<PermissionSet> permissionSets = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_allowed_estates",
            joinColumns = @JoinColumn(name = "username"),
            inverseJoinColumns = @JoinColumn(name = "estate_id")
    )
    private Set<Estate> allowedEstates = new HashSet<>();

    public AppUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void addPermissionSet(PermissionSet permissionSet) {
        this.permissionSets.add(permissionSet);
    }

    public void removePermissionSet(PermissionSet permissionSet) {
        this.permissionSets.remove(permissionSet);
    }
}
