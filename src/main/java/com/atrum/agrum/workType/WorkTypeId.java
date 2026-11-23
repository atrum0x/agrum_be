package com.atrum.agrum.workType;

import java.io.Serializable;

/** A Work Type ID is unique within its site (FSD SB-16), so the key is the site and the ID. */
public record WorkTypeId(String estateId, String workTypeCode) implements Serializable {
}
