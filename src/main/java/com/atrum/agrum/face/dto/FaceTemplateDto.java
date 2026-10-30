package com.atrum.agrum.face.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FaceTemplateDto(
        String employeeCode,
        // "arcface_512"
        String model,
        // Base64 of 512 float32 values, little-endian
        String embedding,
        int sampleCount,
        Instant registeredAt,
        // Set by the server; ignored when the phone sends it
        String registeredBy,
        // Base64 JPEG of the face; sent by the phone, never in the download (phones don't need it)
        String photo
) {}
