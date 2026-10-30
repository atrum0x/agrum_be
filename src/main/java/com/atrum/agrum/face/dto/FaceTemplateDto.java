package com.atrum.agrum.face.dto;

import java.time.Instant;

public record FaceTemplateDto(
        String employeeCode,
        // "arcface_512"
        String model,
        // Base64 of 512 float32 values, little-endian
        String embedding,
        int sampleCount,
        Instant registeredAt,
        // Set by the server; ignored when the phone sends it
        String registeredBy
) {}
