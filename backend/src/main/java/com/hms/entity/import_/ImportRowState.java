package com.hms.entity.import_;

/**
 * Outcome of one spreadsheet row. Every state other than CREATED/UPDATED carries an
 * {@link ImportReasonCode}, so the administrator is told what happened to row 57 rather than
 * "the import failed".
 *
 * <p>SKIPPED and NEEDS_REVIEW are deliberately distinct: SKIPPED is a deterministic no-op that
 * needs nobody's attention (a repeated row in the file, a row identical to the current record);
 * NEEDS_REVIEW means a human has to decide, and hiding that inside a skip count would be guessing
 * by omission.
 */
public enum ImportRowState {
    CREATED, UPDATED, SKIPPED, NEEDS_REVIEW, FAILED
}
