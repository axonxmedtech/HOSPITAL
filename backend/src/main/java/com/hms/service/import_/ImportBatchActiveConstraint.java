package com.hms.service.import_;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognises V24's {@code uk_import_batch_active} in a persistence failure — the one constraint
 * that means "another live batch for this file already exists". The same cause-chain convention
 * as {@code DuplicatePhoneConstraint}: the driver's message names the index it refused on, and
 * nothing else in the chain is inspected. Any other constraint failure is not this condition and
 * must be left alone.
 */
public final class ImportBatchActiveConstraint {

    public static final String INDEX_NAME = "uk_import_batch_active";

    private ImportBatchActiveConstraint() {}

    public static boolean isViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(INDEX_NAME)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
