package com.atrum.agrum.collectionPoint;

import java.io.Serializable;

/** A collection point is unique within its site (FSD SB-09), so its key is the site and the code. */
public record CollectionPointId(String estateId, String cpCode) implements Serializable {
}
