package com.atrum.agrum.workType;

import com.atrum.agrum.estate.Estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A site's work type. The code is unique within the site (FSD SB-11), so two sites can both have
 * PLUCKING. Not an EstateSecuredEntity because the site is part of the key; the estate filter is
 * declared here instead.
 */
@Setter
@Getter
@Entity
@Table(name = "work_types")
@IdClass(WorkTypeId.class)
@Filter(name = "estateSecurityFilter", condition = "estate_id IN (:allowedEstates)")
public class WorkType {

    @Id
    @Column(name = "estate_id", nullable = false)
    private String estateId;

    // The code cannot be changed once created
    @Id
    @Column(name = "work_type", length = 50)
    private String workType;

    // Written through estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    @Column(name = "weighing_required", nullable = false)
    private boolean weighingRequired;
}
