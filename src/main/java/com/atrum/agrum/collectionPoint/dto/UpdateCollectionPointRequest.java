package com.atrum.agrum.collectionPoint.dto;

public record UpdateCollectionPointRequest(
        String name,
        String description,
        Boolean isDefault
) {}
