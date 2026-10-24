package com.atrum.agrum.projection;

import com.atrum.agrum.permission.PermissionSet;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projections", indexes = {
        @Index(name = "idx_projection_method_path", columnList = "http_method, url_path")
})
@Getter
@Setter
@NoArgsConstructor
public class Projection {

    @Id
    @Column(name = "projection_id", nullable = false, unique = true)
    private String id; // e.g. "GET:/api/users/{id}"

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "url_path", nullable = false)
    private String urlPath;

    // Plain-language summary for administrators, from the endpoint's @Operation summary
    @Column(name = "description", length = 300)
    private String description;

    // The window the endpoint belongs to, from the controller's @ProjectionGroup
    @Column(name = "group_name", length = 100)
    private String groupName;

    @ManyToMany(mappedBy = "projections")
    @JsonIgnoreProperties("projections")
    private Set<PermissionSet> permissionSets = new HashSet<>();

    public Projection(String id, String httpMethod, String urlPath) {
        this.id = id;
        this.httpMethod = httpMethod;
        this.urlPath = urlPath;
    }
}