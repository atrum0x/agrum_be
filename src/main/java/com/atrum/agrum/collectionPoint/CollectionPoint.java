package com.atrum.agrum.collectionPoint;

import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.security.EstateSecuredEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "collection_points")
public class CollectionPoint extends EstateSecuredEntity {
    @Id
    private String cpCode;

    // Written through EstateSecuredEntity.estateId; this mapping is read-only
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
