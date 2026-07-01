package com.hms.security;

/**
 * UserRole - Static constants defining all user roles in the system.
 * Unifies string literals to ensure type safety in authorization checks.
 *
 * @author HMS Team
 * @version Phase-0.6
 */
public class UserRole {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String HOSPITAL_ADMIN = "HOSPITAL_ADMIN";
    public static final String DOCTOR = "DOCTOR";
    public static final String RECEPTIONIST = "RECEPTIONIST";
    public static final String NURSE = "NURSE";
    public static final String PHARMACIST = "PHARMACIST";
    public static final String LAB_TECHNICIAN = "LAB_TECHNICIAN";
    public static final String RADIOLOGY_TECHNICIAN = "RADIOLOGY_TECHNICIAN";

    // Extensible clinical and support roles (roadmap §1.2)
    public static final String MRD_OFFICER = "MRD_OFFICER";
    public static final String QUALITY_OFFICER = "QUALITY_OFFICER";
    public static final String DEPARTMENT_HEAD = "DEPARTMENT_HEAD";
    public static final String STORE_KEEPER = "STORE_KEEPER";
    public static final String PURCHASE_OFFICER = "PURCHASE_OFFICER";
    public static final String BIOMEDICAL_ENGINEER = "BIOMEDICAL_ENGINEER";
    public static final String CSSD_TECHNICIAN = "CSSD_TECHNICIAN";
    public static final String BLOOD_BANK_TECHNICIAN = "BLOOD_BANK_TECHNICIAN";
    public static final String HOUSEKEEPING = "HOUSEKEEPING";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String HR_EXECUTIVE = "HR_EXECUTIVE";
    public static final String IT_ADMIN = "IT_ADMIN";
}
