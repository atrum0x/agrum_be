package com.atrum.agrum.collectionPoint.dto;

public record CreateCollectionPointRequest(
        String cpCode,
        String name,
        String description,
        Boolean isDefault
) {}
