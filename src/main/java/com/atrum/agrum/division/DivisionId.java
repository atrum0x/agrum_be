package com.atrum.agrum.division;

import java.io.Serializable;

/** A division is unique within its site (FSD SB-15), so its key is the site and the code. */
public record DivisionId(String estateId, String divisionCode) implements Serializable {
}
