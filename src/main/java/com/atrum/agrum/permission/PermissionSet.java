package com.atrum.agrum.permission;

import com.atrum.agrum.projection.Projection;
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
@Table(name = "permission_sets")
@Getter
@Setter
@NoArgsConstructor
public class PermissionSet {

    @Id
    @Column(name = "permission_set_id", nullable = false, unique = true)
    private String id; // e.g. "HR_ADMIN", "SALES_VIEWER"

    @Column(name = "description")
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "permission_set_grants",
            joinColumns = @JoinColumn(name = "permission_set_id"),
            inverseJoinColumns = @JoinColumn(name = "projection_id")
    )
    private Set<Projection> projections = new HashSet<>();

    public PermissionSet(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public void addProjection(Projection projection) {
        this.projections.add(projection);
        projection.getPermissionSets().add(this);
    }

    public void removeProjection(Projection projection) {
        this.projections.remove(projection);
        projection.getPermissionSets().remove(this);
    }
}
