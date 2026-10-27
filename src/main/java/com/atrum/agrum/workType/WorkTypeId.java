package com.atrum.agrum.workType;

import java.io.Serializable;

/** A work type is unique within its site (FSD SB-11), so its key is the site and the code. */
public record WorkTypeId(String estateId, String workType) implements Serializable {
}
