package com.atrum.agrum.security;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Setter
@Getter
@MappedSuperclass
// 1. Define the filter and the parameter it expects (a list of estate IDs)
@FilterDef(
        name = "estateSecurityFilter",
        parameters = @ParamDef(name = "allowedEstates", type = String.class)
)
// 2. Define the SQL condition that gets appended to your queries
@Filter(
        name = "estateSecurityFilter",
        condition = "estate_id IN (:allowedEstates)"
)
public abstract class EstateSecuredEntity {

    @Column(name = "estate_id", nullable = false)
    private String estateId;

}
