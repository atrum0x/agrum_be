package com.atrum.agrum.face.dto;

import java.time.Instant;

/** A worker's registered face as the web shows it: when and by whom, without the embedding. */
public record FaceRegistrationDto(
        String employeeCode,
        Instant registeredAt,
        String registeredBy,
        int sampleCount,
        // False for faces registered before phones sent a photo
        boolean hasPhoto
) {}
