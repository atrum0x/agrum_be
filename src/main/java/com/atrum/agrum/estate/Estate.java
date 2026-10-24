package com.atrum.agrum.estate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@Entity
@Table(name = "estates")
public class Estate {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private String id; // e.g., "EST_NW_01" or "ESTATE_A"

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = true)
    private String address;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "full_day_qty_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal fullDayQtyKg;

    @Column(name = "half_day_qty_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal halfDayQtyKg;

    @Column(name = "max_daily_transactions", nullable = false)
    private Integer maxDailyTransactions;

    @Column(name = "variance_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal variancePercent;

    public Estate() {}

    public Estate(String id, String name) {
        this.id = id;
        this.name = name;
    }

}