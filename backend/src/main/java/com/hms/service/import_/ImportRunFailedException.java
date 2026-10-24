package com.hms.service.import_;

/**
 * An infrastructure failure stopped a commit part-way. The batch was marked FAILED if the
 * database allowed it; the cause is the original exception. Rows already written stay written —
 * their lineage names the batch — and nothing was retried.
 */
public class ImportRunFailedException extends RuntimeException {

    private final String batchPublicId;

    public ImportRunFailedException(String batchPublicId, Throwable cause) {
        super("Import " + batchPublicId + " stopped: " + cause.getClass().getSimpleName(), cause);
        this.batchPublicId = batchPublicId;
    }

    public String getBatchPublicId() { return batchPublicId; }
}
