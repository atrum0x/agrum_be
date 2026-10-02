package com.atrum.agrum.user;

import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.permission.PermissionSet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
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
