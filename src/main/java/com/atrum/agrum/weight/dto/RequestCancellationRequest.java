package com.atrum.agrum.weight.dto;

public record RequestCancellationRequest(
        String comment,
        // The version the user opened, for WH-10
        Integer rowVersion
) {}
