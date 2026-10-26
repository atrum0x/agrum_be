package com.atrum.agrum.mobile.dto;

import java.math.BigDecimal;

public record MobileEstateDto(
        String id,
        String name,
        BigDecimal variancePercent,
        Integer maxDailyTransactions,
        BigDecimal fullDayQtyKg,
        BigDecimal halfDayQtyKg
) {}
