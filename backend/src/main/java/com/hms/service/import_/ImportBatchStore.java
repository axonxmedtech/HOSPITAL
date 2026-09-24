package com.hms.service.import_;

import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportEntityType;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.ImportRowResultRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The batch row's lifecycle, each step in its own short transaction, reached cross-bean from
 * {@link ImportEngine}. None of these joins a patient-row transaction (REQUIRES_NEW), so a batch
 * update can never be dragged down by a row that failed, and a heartbeat written mid-run is
 * committed even if the run dies a second later.
 *
 * <p><b>Same-file concurrency.</b> {@link #start} does not check-then-insert; it inserts and lets
 * V24's {@code uk_import_batch_active (hospital_id, file_sha256, active_marker)} decide. Two
 * simultaneous starts for one file race at the INSERT; the loser's transaction is refused and it
 * reports the batch that won. A FAILED or UNDONE batch has a NULL marker and never collides, which
 * is what keeps retries allowed.
 */
@Component
public class ImportBatchStore {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchStore.class);

    /** A RUNNING batch whose heartbeat is older than this is abandoned. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(30);
    public static final String ABANDONED_REASON = "SYSTEM: abandoned import";
    public static final String PROCESSING_FAILED_REASON = "SYSTEM: import processing failed";

    private final ImportBatchRepository batches;
    private final ImportRowResultRepository results;

    public ImportBatchStore(ImportBatchRepository batches, ImportRowResultRepository results) {
        this.batches = batches;
        this.results = results;
    }

    /** The batch a commit of these bytes would collide with, if any. Preview reports it; commit refuses. */
    @Transactional(readOnly = true)
    public Optional<ImportBatch> findLive(Long hospitalId, String sha256) {
        return batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(hospitalId, sha256, ImportStatus.ALREADY_IMPORTED);
    }

    /**
     * Inserts the RUNNING batch.
     *
     * @throws DataIntegrityViolationException when a live batch for these bytes exists — decided by
     *         the unique index, so it holds under concurrency. It escapes this transaction (which
     *         rolls back) and the engine turns it into {@link AlreadyImportedException} with a
     *         fresh read; nothing is queried in the session the flush just broke.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch start(ImportCommitRequest request, String sha256, String mappingJson, LocalDateTime now) {
        ImportBatch b = new ImportBatch();
        b.setHospitalId(request.hospitalId());
        b.setEntityType(ImportEntityType.PATIENT);
        b.setSourceFilename(request.sourceFilename());
        b.setSheetName(request.sheetName());
        b.setMappingJson(mappingJson);
        b.setFileSha256(sha256);
        b.setCreatedBy(request.createdBy());
        b.setStatus(ImportStatus.RUNNING);
        b.setHeartbeatAt(now);
        return batches.saveAndFlush(b);
    }

    /**
     * Called by the engine after {@link #start} was refused by {@code uk_import_batch_active}: that
     * transaction has rolled back, so this read is a fresh one. A visible winner is named; a winner
     * still committing is reported as in progress with no details — never with a made-up id.
     */
    public AlreadyImportedException alreadyImported(Long hospitalId, String sha256) {
        return findLive(hospitalId, sha256)
                .map(b -> AlreadyImportedException.visible(b.getPublicId(), b.getStatus(), b.getCommittedAt()))
                .orElseGet(AlreadyImportedException::inProgressButNotYetVisible);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(Long hospitalId, Long batchId, ImportCounters.Snapshot counts, LocalDateTime now) {
        ImportBatch b = own(hospitalId, batchId);
        apply(b, counts);
        b.setHeartbeatAt(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch finish(Long hospitalId, Long batchId, ImportCounters.Snapshot counts, LocalDateTime now) {
        ImportBatch b = own(hospitalId, batchId);
        apply(b, counts);
        b.setStatus(counts.needsReview() == 0 && counts.failed() == 0 ? ImportStatus.COMPLETED : ImportStatus.PARTIAL);
        b.setCommittedAt(now);
        b.setHeartbeatAt(now);
        return b;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long hospitalId, Long batchId, String reason, ImportCounters.Snapshot counts, LocalDateTime now) {
        ImportBatch b = own(hospitalId, batchId);
        if (counts != null) apply(b, counts);
        b.setStatus(ImportStatus.FAILED);
        b.setFailureReason(reason);
        b.setHeartbeatAt(now);
    }

    /**
     * Moves every abandoned RUNNING batch of the hospital to FAILED, recomputing its counters from
     * the persisted row results. Nothing is deleted. If the lineage shows the batch touched more
     * patients than it recorded results for, that is logged as a recovery concern — the counters
     * are never made up to cover it.
     *
     * @return the batches resolved
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ImportBatch> resolveStale(Long hospitalId, LocalDateTime now) {
        List<ImportBatch> stale = batches.findByHospitalIdAndStatusAndHeartbeatAtBefore(hospitalId, ImportStatus.RUNNING, now.minus(STALE_AFTER));
        for (ImportBatch b : stale) {
            Map<ImportRowState, Long> byState = new EnumMap<>(ImportRowState.class);
            for (Object[] row : results.countByStateForBatch(b.getId())) byState.put((ImportRowState) row[0], (Long) row[1]);
            ImportCounters c = new ImportCounters();
            byState.forEach((state, n) -> { for (long i = 0; i < n; i++) c.count(state); });
            apply(b, c.snapshot());
            b.setStatus(ImportStatus.FAILED);
            b.setFailureReason(ABANDONED_REASON);
            b.setHeartbeatAt(now);
            long touched = batches.countLinksTouchedBy(hospitalId, b.getId());
            long recorded = (long) c.created() + c.updated();
            if (touched > recorded) {
                log.warn("Import {} abandoned: lineage shows {} patient(s) written but only {} success result(s) recorded — recovery review needed",
                        b.getPublicId(), touched, recorded);
            } else {
                log.info("Import {} abandoned after no heartbeat since {}; marked FAILED", b.getPublicId(), b.getHeartbeatAt());
            }
        }
        return stale;
    }

    /** Every batch load is tenant-scoped, even for the engine's own batch: an id alone is never enough. */
    private ImportBatch own(Long hospitalId, Long batchId) {
        return batches.findByIdAndHospitalId(batchId, hospitalId)
                .orElseThrow(() -> new IllegalStateException("Import batch " + batchId + " is not this hospital's"));
    }

    private static void apply(ImportBatch b, ImportCounters.Snapshot c) {
        b.setTotalRows(c.total());
        b.setCreatedCount(c.created());
        b.setUpdatedCount(c.updated());
        b.setSkippedCount(c.skipped());
        b.setNeedsReviewCount(c.needsReview());
        b.setFailedCount(c.failed());
    }
}
