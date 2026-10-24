package com.hms.service.import_;

import com.hms.entity.import_.ImportStatus;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A live batch for these exact bytes exists for this hospital, so the commit was refused.
 *
 * <p>Two honest shapes. When the winning batch is visible, {@link #getBatchPublicId()} carries
 * its real public id with its status and commit time. When V24's unique index proved a live
 * batch exists but the loser's fresh lookup cannot see it yet — the winner's own transaction is
 * still committing — the condition is {@link Condition#IMPORT_ALREADY_IN_PROGRESS} with
 * {@code detailsAvailable=false}: no id, no commit time, and never a fabricated one. A caller
 * should tell the user an import of this file is already starting and to retry shortly.
 * Nothing internal (database ids) is ever carried.
 */
public class AlreadyImportedException extends RuntimeException {

    public enum Condition {
        /** A visible COMPLETED, PARTIAL or RUNNING batch for the file. */
        ALREADY_IMPORTED,
        /** The database refused a second live batch, but the first is not yet visible. */
        IMPORT_ALREADY_IN_PROGRESS
    }

    private final Condition condition;
    private final String batchPublicId;
    private final ImportStatus status;
    private final LocalDateTime committedAt;

    private AlreadyImportedException(Condition condition, String batchPublicId, ImportStatus status, LocalDateTime committedAt, String message) {
        super(message);
        this.condition = condition;
        this.batchPublicId = batchPublicId;
        this.status = status;
        this.committedAt = committedAt;
    }

    public static AlreadyImportedException visible(String batchPublicId, ImportStatus status, LocalDateTime committedAt) {
        return new AlreadyImportedException(
                Condition.ALREADY_IMPORTED,
                batchPublicId,
                status,
                committedAt,
                status == ImportStatus.RUNNING
                        ? "This file is being imported right now (import " + batchPublicId + ")."
                        : "This exact file was already imported as " + batchPublicId + " (" + status + ").");
    }

    public static AlreadyImportedException inProgressButNotYetVisible() {
        return new AlreadyImportedException(
                Condition.IMPORT_ALREADY_IN_PROGRESS, null, ImportStatus.RUNNING, null,
                "An import of this file is already starting. Retry shortly.");
    }

    public Condition getCondition() { return condition; }

    /** True when a real batch could be named; false for the in-progress-but-invisible case. */
    public boolean isDetailsAvailable() { return batchPublicId != null; }

    /** The winning batch's public id, or empty when it was not yet visible. Never fabricated. */
    public Optional<String> getBatchPublicId() { return Optional.ofNullable(batchPublicId); }

    public ImportStatus getStatus() { return status; }

    public Optional<LocalDateTime> getCommittedAt() { return Optional.ofNullable(committedAt); }
}
