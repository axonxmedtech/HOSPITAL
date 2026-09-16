package com.hms.entity.import_;

/**
 * Stable, machine-readable reason for a non-success row outcome. Stored by name in
 * {@code import_row_results.reason_code}; the frontend and the review CSV key off these strings,
 * so renaming one is an API change.
 *
 * <p>Codes are grouped by the {@link ImportRowState} they belong to. Nothing here ever carries SQL
 * text, constraint names or stack traces — those stay in server logs keyed by batch id.
 */
public enum ImportReasonCode {

    // SKIPPED — deterministic, no human action needed
    DUPLICATE_MRN_IN_FILE,
    NO_CHANGE,

    // NEEDS_REVIEW — a human must decide
    PHONE_MISSING,
    PHONE_UNRECOVERABLE,
    DUPLICATE_PHONE_IN_FILE,
    DUPLICATE_PHONE_REQUIRES_REVIEW,
    DUPLICATE_PHONE_RACE,
    AMBIGUOUS_PATIENT_MATCH,
    INACTIVE_MATCH,
    MRN_IDENTITY_MISMATCH,
    EDITED_SINCE_IMPORT,
    INVALID_GENDER,

    // FAILED — the row could not be applied as given
    NAME_MISSING,
    INVALID_DOB,
    VALIDATION_FAILED,
    CONSTRAINT_FAILED,
    UNEXPECTED_ERROR
}
