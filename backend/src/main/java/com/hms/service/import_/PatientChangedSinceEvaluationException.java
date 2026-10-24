package com.hms.service.import_;

/**
 * Thrown inside the row transaction when the reloaded patient no longer matches the state the
 * proposal was evaluated against — another user edited it, deactivated it, or it is gone. Being
 * unchecked it rolls the row transaction back (nothing has been written by then anyway); the
 * coordinator turns it into NEEDS_REVIEW: PATIENT_CHANGED_SINCE_EVALUATION.
 */
public class PatientChangedSinceEvaluationException extends RuntimeException {

    private final Long patientId;
    private final String what;

    public PatientChangedSinceEvaluationException(Long patientId, String what) {
        super("Patient " + patientId + " changed since evaluation: " + what);
        this.patientId = patientId;
        this.what = what;
    }

    public Long getPatientId() {
        return patientId;
    }

    /** Which check failed — a field name, "active", "acknowledgement", "missing" — never a value. */
    public String getWhat() {
        return what;
    }
}
