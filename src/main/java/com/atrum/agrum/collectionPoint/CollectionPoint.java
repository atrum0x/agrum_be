package com.atrum.agrum.collectionPoint;

import com.atrum.agrum.estate.Estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A site's collection point. The code is unique within the site (FSD SB-09). Not an
 * EstateSecuredEntity because the site is part of the key; the estate filter is declared here instead.
 */
@Setter
@Getter
@Entity
@Table(name = "collection_points")
@IdClass(CollectionPointId.class)
@Filter(name = "estateSecurityFilter", condition = "estate_id IN (:allowedEstates)")
public class CollectionPoint {

    @Id
    @Column(name = "estate_id", nullable = false)
    private String estateId;

    @Id
    @Column(name = "cp_code")
    private String cpCode;

    // Written through estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    // Exactly one collection point per estate is the default
    @Column(name = "is_default")
    private boolean isDefault;
}
