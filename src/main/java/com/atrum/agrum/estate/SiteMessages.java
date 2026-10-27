package com.atrum.agrum.estate;

/**
 * Error messages from Site Basic Data (FSD v2.4 section 2.3 D), keyed by validation id.
 * SB-14 (Edit permission) is the endpoint grant.
 */
public final class SiteMessages {

    public static final String SB_01_SITE_CODE = "Please enter the Site Code.";
    public static final String SB_02_SITE_EXISTS = "Site Code already exists. Please enter a different code.";
    public static final String SB_03_SITE_NAME = "Please enter the Site Name.";
    public static final String SB_04_FULL_DAY = "Full-Day Quantity must be greater than 0.";
    public static final String SB_05_HALF_DAY = "Half-Day Quantity must be greater than 0.";
    public static final String SB_06_HALF_OVER_FULL = "Half-Day Quantity cannot be greater than the Full-Day Quantity.";
    public static final String SB_07_MAX_TRANSACTIONS = "Maximum Weight Transactions must be a whole number of 1 or more.";
    public static final String SB_08_VARIANCE = "Please enter a valid variance percentage (for example 2 or -2).";
    public static final String SB_09_COLLECTION_POINT_EXISTS = "This Collection Point already exists for this site.";
    public static final String SB_11_WORK_TYPE_EXISTS = "This Work Type already exists for this site.";
    public static final String SB_12_IN_USE = "This item is in use and cannot be changed or removed.";
    public static final String SB_13_WEIGHED_TODAY = "Weighing has already been recorded today for this work type.";

    private SiteMessages() {}
}
