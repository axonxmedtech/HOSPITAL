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
    /** A new patient needs a date of birth; the column was unmapped or the cell blank. */
    DOB_MISSING,
    DUPLICATE_PHONE_IN_FILE,
    DUPLICATE_PHONE_REQUIRES_REVIEW,
    DUPLICATE_PHONE_RACE,
    /** The patient was changed in HMS between evaluation and the write; the proposal was not applied. */
    PATIENT_CHANGED_SINCE_EVALUATION,
    AMBIGUOUS_PATIENT_MATCH,
    INACTIVE_MATCH,
    MRN_IDENTITY_MISMATCH,
    EDITED_SINCE_IMPORT,
    INVALID_GENDER,

    // FAILED — the row could not be applied as given
    NAME_MISSING,
    INVALID_DOB,
    /** The parser dropped a cell over the resource cap; the row's data is not trustworthy. */
    VALUE_TOO_LONG,
    /** A value sat in a column with no header; the parser could not name it. */
    UNEXPECTED_COLUMN,
    VALIDATION_FAILED,
    CONSTRAINT_FAILED,
    UNEXPECTED_ERROR
}
