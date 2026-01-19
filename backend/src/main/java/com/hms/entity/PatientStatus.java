package com.hms.entity;

/**
 * PatientStatus - Enum for tracking patient consultation lifecycle
 * 
 * REGISTERED - Patient has been registered (initial state)
 * CONSULTING - Doctor has started consultation with patient
 * COMPLETED - Consultation finished, prescription given, ready for billing
 * 
 * @author HMS Team
 * @version Phase-2
 */
public enum PatientStatus {
    REGISTERED, // Initial state when patient is added
    CONSULTING, // Doctor has started consultation
    COMPLETED // Consultation finished, prescription given
}
