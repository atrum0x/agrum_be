package com.atrum.agrum.employee;

/**
 * Error messages from Person Basic Data (FSD v2.4 section 2.4 D), keyed by validation id.
 * Assigned Sites (PB-09) are the sites granted to the person's login; a person without a login has
 * only their Default Site. PB-13 (authorization group) is the permission sets; PB-15 (Edit
 * permission) is the endpoint grant.
 */
public final class PersonMessages {

    public static final String PB_01_EMPLOYEE_NO = "Please enter the Employee No.";
    public static final String PB_01_EMPLOYEE_NO_EXISTS = "Employee No already exists. Please enter a different number.";
    public static final String PB_02_NAME = "Please enter the Name.";
    public static final String PB_03_NIC = "Please enter the NIC.";
    public static final String PB_04_NIC_EXISTS = "A person with this NIC already exists (Employee No: %s).";
    public static final String PB_05_CATEGORY = "Please select the Employment Category.";
    public static final String PB_06_START_DATE = "Please enter a valid Start Date.";
    public static final String PB_07_END_DATE = "End Date cannot be earlier than Start Date.";
    public static final String PB_08_HOME_SITE = "Please select the Home Site.";
    public static final String PB_10_DEFAULT_SITE = "Default Site must be one of the Assigned Sites.";
    public static final String PB_11_DEFAULT_COLLECTION_POINT = "Default Collection Point must belong to the Default Site.";

    private PersonMessages() {}

    public static String nicExists(String employeeCode) {
        return PB_04_NIC_EXISTS.formatted(employeeCode);
    }
}
