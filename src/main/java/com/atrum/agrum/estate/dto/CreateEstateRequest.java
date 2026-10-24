package com.atrum.agrum.estate.dto;

import java.math.BigDecimal;

public record CreateEstateRequest(
        String id,
        String name,
        String address,
        Boolean active,
        BigDecimal fullDayQtyKg,
        BigDecimal halfDayQtyKg,
        Integer maxDailyTransactions,
        BigDecimal variancePercent
) {}
