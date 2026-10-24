package com.atrum.agrum.attendance;

/**
 * Error messages from the attendance spec (FSD v2.4 section 2.6 D), keyed by validation id.
 */
public final class AttendanceMessages {

    public static final String AT_01_NO_WORKERS = "Please select at least one worker.";
    public static final String AT_02_ALREADY_RECORDED = "Attendance has already been recorded today for %s.";
    public static final String AT_03_INACTIVE_WORKER = "This worker is inactive and cannot be selected.";
    public static final String AT_04_NOT_EMPLOYED = "This worker is not employed on this date.";
    public static final String AT_05_SITE_NOT_ALLOWED = "Attendance cannot be recorded for an inactive or unassigned site.";
    public static final String AT_06_OTHER_SITE = "You are not permitted to record %s at a different site.";
    public static final String AT_07_WORK_TYPE = "Please select a Work Type.";
    public static final String AT_08_CATEGORY = "Please select the Attendance Category.";
    public static final String AT_09_COLLECTION_POINT = "Please select the Collection Point.";
    public static final String AT_10_SITE_SETUP = "Site setup is incomplete. Please contact the administrator.";
    public static final String AT_11_TODAY_ONLY = "Attendance can only be recorded for today. Please check the phone date.";
    public static final String AT_15_REASON = "Please enter the reason for the correction.";
    public static final String AT_16_WEIGHINGS_EXIST =
            "Weights are recorded for this worker. Cancel them before changing to a non-weighing work type.";
    public static final String AT_17_CANNOT_DELETE = "This attendance has weight records and cannot be deleted.";
    public static final String AT_18_CONCURRENT_UPDATE = "This record was updated by another user. Please refresh and try again.";

    private AttendanceMessages() {}

    public static String alreadyRecorded(String worker) {
        return AT_02_ALREADY_RECORDED.formatted(worker);
    }

    public static String otherSite(String worker) {
        return AT_06_OTHER_SITE.formatted(worker);
    }
}
