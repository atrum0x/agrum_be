package com.atrum.agrum.weight.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The server clock the phone checks its date against (D-10).
 */
public record WeighingDefaultsDto(
        LocalDate today,
        Instant serverTime
) {}
