package com.atrum.agrum.collectionPoint.dto;

import com.atrum.agrum.collectionPoint.CollectionPoint;

public record CollectionPointDto(
        String cpCode,
        String estateId,
        String name,
        String description,
        boolean isDefault
) {
    public static CollectionPointDto from(CollectionPoint collectionPoint) {
        return new CollectionPointDto(
                collectionPoint.getCpCode(),
                collectionPoint.getEstateId(),
                collectionPoint.getName(),
                collectionPoint.getDescription(),
                collectionPoint.isDefault());
    }
}
