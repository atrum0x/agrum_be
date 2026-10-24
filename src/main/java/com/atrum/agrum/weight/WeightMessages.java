package com.atrum.agrum.weight;

/**
 * Error messages from the weight spec (FSD v2.4 sections 2.7–2.9), keyed by validation id.
 */
public final class WeightMessages {

    // Enter Weight Management (2.7)
    public static final String WE_01_SELECT_WORKER = "Please select a worker.";
    public static final String WE_02_NO_ATTENDANCE =
            "No attendance found for this worker today. Please record attendance first.";
    public static final String WE_03_NOT_WEIGHING = "This worker's work type today does not require weighing.";
    public static final String WE_04_LIMIT_REACHED = "Maximum of %d weighings per day already recorded for this worker.";
    public static final String WE_05_INACTIVE_SITE = "Weights cannot be recorded for an inactive site.";
    public static final String WE_06_WEIGHT = "Please enter a weight greater than 0.";
    public static final String WE_07_NO_SCALE_READING = "No valid reading from the scale. Please check the scale connection.";
    public static final String WE_08_NO_MANUAL = "You are not permitted to enter weight manually.";
    public static final String WE_11_NO_PERMISSION = "You do not have permission to record weights.";
    // D-10
    public static final String TODAY_ONLY = "Weights can only be recorded for today. Please check the phone date.";
    public static final String ENTRY_TYPE = "Please capture the weight from the scale or enter it manually.";

    // Weight History Management (2.8)
    public static final String WH_01_DATES = "Date From cannot be later than Date To.";
    public static final String WH_03_NO_PERMISSION = "You do not have permission to change weight records.";
    public static final String WH_04_CANCELLED = "This weight record has been cancelled and cannot be changed.";
    public static final String WH_05_ALREADY_PENDING = "A change for this record is already pending authorization.";
    public static final String WH_06_CORRECTED_WEIGHT =
            "Please enter a corrected weight greater than 0 and different from the current weight.";
    public static final String WH_07_COMMENT = "Please enter a comment explaining the change.";
    public static final String WH_08_NO_PERMISSION = "You do not have permission to approve weight changes.";
    public static final String WH_09_NOT_PENDING = "This request is no longer pending authorization.";
    public static final String WH_10_CONCURRENT_UPDATE = "This record was updated by another user. Please refresh and try again.";

    // Daily Weight Summary (2.9)
    public static final String DS_01_DATES = "Date From cannot be later than Date To.";
    public static final String DS_04_NO_RECORDS = "No records to export.";

    private WeightMessages() {}

    public static String limitReached(int limit) {
        return WE_04_LIMIT_REACHED.formatted(limit);
    }
}
