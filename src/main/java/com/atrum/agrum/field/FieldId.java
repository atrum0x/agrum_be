package com.atrum.agrum.field;

import java.io.Serializable;

/** A field is unique within its site (FSD SB-09), so its key is the site and the code. */
public record FieldId(String estateId, String fieldCode) implements Serializable {
}
