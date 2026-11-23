package com.atrum.agrum.field;

import com.atrum.agrum.division.Division;
import com.atrum.agrum.estate.Estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A site's field: where workers bring their tea for weighing (FSD D-01; formerly "collection point").
 * Belongs to one division of the site. The code is unique within the site (SB-09), so the key is
 * (site, code) and a field can move to another division. Not an EstateSecuredEntity because the
 * site is part of the key; the estate filter is declared here instead.
 */
@Setter
@Getter
@Entity
@Table(name = "fields")
@IdClass(FieldId.class)
@Filter(name = "estateSecurityFilter", condition = "estate_id IN (:allowedEstates)")
public class Field {

    @Id
    @Column(name = "estate_id", nullable = false)
    private String estateId;

    @Id
    @Column(name = "field_code", length = 30)
    private String fieldCode;

    // Written through estateId; this mapping is read-only
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estate_id", insertable = false, updatable = false)
    private Estate estate;

    // Must be a division of the same site. The code is written here; this mapping is read-only
    @Column(name = "division_code", nullable = false, length = 30)
    private String divisionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "estate_id", referencedColumnName = "estate_id", insertable = false, updatable = false),
            @JoinColumn(name = "division_code", referencedColumnName = "division_code", insertable = false, updatable = false)
    })
    private Division division;

    @Column(name = "description", nullable = false, length = 50)
    private String description;

    // At most one default field per division (SB-10); a unique partial index enforces it
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public void setDivision(Division division) {
        this.division = division;
        this.divisionCode = division == null ? null : division.getDivisionCode();
    }
}
