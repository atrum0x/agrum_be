package com.atrum.agrum.division;

import com.atrum.agrum.estate.Estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A part of a site; each division has its own fields (FSD D-01). The code is unique within the site
 * (SB-15). Not an EstateSecuredEntity because the site is part of the key; the estate filter is
 * declared here instead.
 */
@Setter
@Getter
@Entity
@Table(name = "divisions")
@IdClass(DivisionId.class)
@Filter(name = "estateSecurityFilter", condition = "estate_id IN (:allowedEstates)")
public class Division {

    @Id
    @Column(name = "estate_id", nullable = false)
    private String estateId;

    @Id
    @Column(name = "division_code", length = 30)
    private String divisionCode;

    // Written through estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    @Column(name = "description", nullable = false, length = 50)
    private String description;
}
