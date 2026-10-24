package com.hms.service.hospital;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognises a violation of {@code uq_patient_active_phone} — the database half of the patient
 * duplicate-phone invariant (S-PID-D).
 *
 * <p>Two registrations can both pass the application's duplicate check and race to INSERT. The
 * loser is rejected by the unique index, and that rejection has to be turned back into the same
 * structured conflict Phase A already returns, so reception sees the chooser rather than a generic
 * "conflicts with existing data".
 *
 * <p>The match is deliberately narrow. Every other constraint on this table — a duplicated
 * public_id, a foreign key, a NOT NULL — means something else entirely, and reporting it as a
 * duplicate phone would hide a real defect behind a friendly message. The index name is matched
 * across the whole nested cause chain, because the string appears in the driver's message
 * ("Duplicate entry '1-9900011111' for key 'patients.uq_patient_active_phone'") several wrappers
 * below the Spring exception.
 */
public final class DuplicatePhoneConstraint {

    /** The unique index created by V21 / DatabaseMigrationRunner. */
    public static final String INDEX_NAME = "uq_patient_active_phone";

    private DuplicatePhoneConstraint() {
    }

    /** True only when this failure is the active-phone uniqueness index rejecting the write. */
    public static boolean isViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(INDEX_NAME)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break; // a self-referencing cause would otherwise loop forever
            }
        }
        return false;
    }
}
