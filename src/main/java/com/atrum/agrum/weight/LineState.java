package com.atrum.agrum.weight;

// Only an approved cancellation moves a weighing to CANCELLED; it then stops counting (WE-04, totals)
public enum LineState {
    RECORDED,
    CANCELLED
}
