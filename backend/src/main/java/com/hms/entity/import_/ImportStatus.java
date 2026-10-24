package com.hms.entity.import_;

/**
 * Lifecycle of an {@link ImportBatch}.
 *
 * <ul>
 *   <li>{@code RUNNING} — rows are being applied; {@code heartbeat_at} advances per chunk. A RUNNING
 *       batch whose heartbeat is older than the stale threshold is abandoned and moved to FAILED
 *       with a system reason before another import may start (never deleted).</li>
 *   <li>{@code COMPLETED} — every row ended CREATED, UPDATED or SKIPPED.</li>
 *   <li>{@code PARTIAL} — finished, but some rows ended NEEDS_REVIEW or FAILED. The rows that did
 *       succeed stay: a bad row never rolls back an unrelated good one.</li>
 *   <li>{@code FAILED} — an infrastructure-level failure or abandonment; persisted counts are still
 *       the truth about what was written.</li>
 *   <li>{@code UNDONE} — the patients this batch CREATED were soft-deleted again.</li>
 * </ul>
 *
 * <p>COMPLETED and PARTIAL block a re-import of the identical file (hospital-scoped SHA-256);
 * FAILED and UNDONE do not, because re-running is exactly how those are recovered.
 */
public enum ImportStatus {
    RUNNING, COMPLETED, PARTIAL, FAILED, UNDONE;

    /** Statuses that count as "this file has already been imported" for the fingerprint check. */
    public static final java.util.List<ImportStatus> ALREADY_IMPORTED =
            java.util.List.of(RUNNING, COMPLETED, PARTIAL);

    public boolean isUndoable() {
        return this == COMPLETED || this == PARTIAL || this == FAILED;
    }
}
