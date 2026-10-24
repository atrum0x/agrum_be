package com.atrum.agrum.weight.dto;

import java.math.BigDecimal;

/**
 * D-07: only the weight can be corrected.
 */
public record RequestCorrectionRequest(
        BigDecimal correctedWeightKg,
        String comment,
        // The version the user opened, for WH-10
        Integer rowVersion
) {}
