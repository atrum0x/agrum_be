package com.atrum.agrum.estate.dto;

public record UpdateEstateRequest(
        String name,
        String address,
        Boolean active,
        Integer fullDayQtyKg,
        Integer halfDayQtyKg,
        Integer maxDailyTransactions,
        Float variancePercent
) {}
