package com.atrum.agrum.workType;

import com.atrum.agrum.estate.Estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A site's work type. The Work Type ID is unique within the site (FSD SB-16), so two sites can both
 * have PLUCKING; the name is unique within the site too (SB-11). Not an EstateSecuredEntity because
 * the site is part of the key; the estate filter is declared here instead.
 */
@Setter
@Getter
@Entity
@Table(name = "work_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_types_estate_name", columnNames = {"estate_id", "name"}))
@IdClass(WorkTypeId.class)
@Filter(name = "estateSecurityFilter", condition = "estate_id IN (:allowedEstates)")
public class WorkType {

    @Id
    @Column(name = "estate_id", nullable = false)
    private String estateId;

    // Work Type ID (SB-16): cannot be changed once created
    @Id
    @Column(name = "work_type_code", length = 20)
    private String workTypeCode;

    // Written through estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    // Shown with the ID in lists; can change while the work type is unused (SB-12)
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "weighing_required", nullable = false)
    private boolean weighingRequired;
}
