package com.hms.service.import_;

import com.hms.entity.import_.ImportStatus;
import java.time.LocalDateTime;

/**
 * The exact same file has already been imported by this hospital (or is being imported right
 * now). Carries only what an administrator may see: the batch's public id, its status and when
 * it committed — never the database id.
 */
public class AlreadyImportedException extends RuntimeException {

    private final String batchPublicId;
    private final ImportStatus status;
    private final LocalDateTime committedAt;

    public AlreadyImportedException(String batchPublicId, ImportStatus status, LocalDateTime committedAt) {
        super(status == ImportStatus.RUNNING
                ? "This file is being imported right now (import " + batchPublicId + ")."
                : "This exact file was already imported as " + batchPublicId + " (" + status + ").");
        this.batchPublicId = batchPublicId;
        this.status = status;
        this.committedAt = committedAt;
    }

    public String getBatchPublicId() { return batchPublicId; }

    public ImportStatus getStatus() { return status; }

    public LocalDateTime getCommittedAt() { return committedAt; }
}
