package com.atrum.agrum.weight.dto;

/**
 * Approve or Disapprove a pending change.
 */
public record DecideWeightChangeRequest(
        // The weighing's version shown in the pop-up, for WH-10
        Integer weightRowVersion
) {}
