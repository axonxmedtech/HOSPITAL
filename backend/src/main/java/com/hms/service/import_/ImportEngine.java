package com.hms.service.import_;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportRowResult;
import com.hms.entity.import_.ImportRowState;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Connects the parser, the evaluator and the row persister into a preview and a commit. It
 * orchestrates and nothing more: every decision about a row is {@link PatientImporter}'s, every
 * patient write is {@link ImportRowPersister}'s, and every batch or result write is a store's.
 *
 * <p><b>Streaming.</b> Both runs consume the parser's push model row by row. Preview keeps
 * counts and at most {@link ImportPreview#MAX_SAMPLES} problem rows; commit keeps at most one
 * chunk of pending results ({@link ImportResultStore#CHUNK_SIZE}). Neither ever holds the file.
 *
 * <p><b>Transactions — there is no outer one.</b> {@code commit} itself is not transactional.
 * Per row, the persister's writer runs its own transaction (Phase 4); per chunk, the result
 * store runs its own (REQUIRES_NEW); the batch store's start/heartbeat/finish/fail each run their
 * own (REQUIRES_NEW). One row's failure therefore cannot roll back another row, a chunk, or the
 * batch record — and a row-local outcome (validation, constraint, a lost phone race, a patient
 * edited since evaluation) is recorded and the run continues.
 *
 * <p><b>Infrastructure failure stops the run.</b> Anything the persister propagates, or a failed
 * result chunk, ends processing at that row: the batch is marked FAILED with a fixed system
 * reason (if the database still answers) and an {@link ImportRunFailedException} carries the cause
 * out. Nothing is retried. If a patient was written and its result chunk then failed, that write
 * stands — its lineage carries this batch's id, which is how recovery finds it; the alternative,
 * a transaction spanning rows, is exactly what a lost phone race must never poison.
 *
 * <p>Logs carry batch ids, counts and exception class names; never a filename together with
 * patient data, never a row.
 */
@Component
public class ImportEngine {

    private static final Logger log = LoggerFactory.getLogger(ImportEngine.class);

    private final WorkbookParser parser;
    private final PatientImporter importer;
    private final ImportRowPersister persister;
    private final ImportBatchStore batchStore;
    private final ImportResultStore resultStore;
    private final ObjectMapper json;
    private final Clock clock;

    public ImportEngine(
            WorkbookParser parser,
            PatientImporter importer,
            ImportRowPersister persister,
            ImportBatchStore batchStore,
            ImportResultStore resultStore,
            ObjectMapper json,
            Clock clock) {
        this.parser = parser;
        this.importer = importer;
        this.persister = persister;
        this.batchStore = batchStore;
        this.resultStore = resultStore;
        this.json = json;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ── preview ─────────────────────────────────────────────────────────────

    /** A dry run. Reads patients and links through the evaluator; writes nothing anywhere. */
    public ImportPreview preview(SpooledUpload upload, ImportFormat format, String sheetName, Map<String, String> mapping, Long hospitalId) {
        ImportEvaluationContext ctx = new ImportEvaluationContext(hospitalId);
        ImportCounters counters = new ImportCounters();
        List<ImportPreview.RowSample> samples = new ArrayList<>();
        boolean[] truncated = {false};
        String[] sheet = {null};
        List<List<String>> headers = new ArrayList<>(List.of(List.of()));

        parser.parse(upload, format, sheetName, new RowSink() {
            SheetHeader header;

            @Override
            public void header(SheetHeader h) {
                header = h;
                sheet[0] = h.sheetName();
                headers.set(0, h.display());
            }

            @Override
            public boolean row(ParsedRow row) {
                RowEvaluation e = importer.evaluate(row, header, mapping, ctx);
                counters.count(e.state());
                if (!e.isSuccess()) {
                    if (samples.size() < ImportPreview.MAX_SAMPLES) {
                        samples.add(new ImportPreview.RowSample(e.rowNum(), e.state(), e.reasonCode(), e.column(), e.message(), e.phoneMasked()));
                    } else {
                        truncated[0] = true;
                    }
                }
                return true;
            }
        });

        ImportPreview.PreviousImport previous;
        try {
            previous = batchStore.findLive(hospitalId, upload.sha256())
                    .map(b -> new ImportPreview.PreviousImport(b.getPublicId(), b.getStatus(), b.getCommittedAt()))
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException("Could not fingerprint the upload", e);
        }
        return new ImportPreview(sheet[0], headers.get(0), counters.snapshot(), List.copyOf(samples), truncated[0], previous);
    }

    // ── commit ──────────────────────────────────────────────────────────────

    /**
     * Imports the file for real.
     *
     * @throws AlreadyImportedException a live batch for these exact bytes exists for this hospital
     * @throws ImportRunFailedException an infrastructure failure stopped the run; the batch is FAILED
     * @throws ImportParseException     the file itself was refused before any row was processed
     */
    public ImportCommitSummary commit(SpooledUpload upload, ImportFormat format, ImportCommitRequest request) {
        Long hospitalId = request.hospitalId();
        String sha;
        try {
            sha = upload.sha256();
        } catch (IOException e) {
            throw new IllegalStateException("Could not fingerprint the upload", e);
        }

        // Abandoned runs first, so a crashed import of this very file does not block its retry forever.
        batchStore.resolveStale(hospitalId, now());

        ImportBatch batch;
        try {
            batch = batchStore.start(request, sha, mappingJson(request.mapping()), now());
        } catch (org.springframework.dao.DataIntegrityViolationException raced) {
            throw batchStore.alreadyImported(hospitalId, sha);
        }
        String publicId = batch.getPublicId();
        log.info("Import {} started for hospital {}", publicId, hospitalId);

        ImportEvaluationContext ctx = new ImportEvaluationContext(hospitalId);
        ImportCounters counters = new ImportCounters();
        ImportWriteContext writeCtx = new ImportWriteContext(hospitalId, batch.getId(), now());
        CommitSink sink = new CommitSink(batch.getId(), request.mapping(), ctx, counters, writeCtx);

        try {
            parser.parse(upload, format, request.sheetName(), sink);
            if (sink.failure != null) throw sink.failure;
            sink.flushPending();
        } catch (ImportParseException e) {
            // The file was refused (after a row or two, possibly): the rows already written stand;
            // the batch is FAILED with the parser's reason class, never its text.
            markFailed(batch, ImportBatchStore.PROCESSING_FAILED_REASON, counters);
            throw e;
        } catch (RuntimeException e) {
            markFailed(batch, ImportBatchStore.PROCESSING_FAILED_REASON, counters);
            log.error("Import {} stopped by {} after {} row(s)", publicId, e.getClass().getSimpleName(), counters.total());
            throw new ImportRunFailedException(publicId, e);
        }

        ImportBatch done = batchStore.finish(hospitalId, batch.getId(), counters.snapshot(), now());
        log.info("Import {} {}: {} created, {} updated, {} skipped, {} review, {} failed",
                publicId, done.getStatus(), counters.created(), counters.updated(), counters.skipped(), counters.needsReview(), counters.failed());
        return new ImportCommitSummary(publicId, done.getStatus(), counters.snapshot(), done.getCommittedAt());
    }

    private void markFailed(ImportBatch batch, String reason, ImportCounters counters) {
        try {
            batchStore.fail(batch.getHospitalId(), batch.getId(), reason, counters.snapshot(), now());
        } catch (RuntimeException dbDown) {
            // The database will not even take the FAILED mark; stale-RUNNING recovery handles it later.
            log.error("Import {} could not be marked FAILED: {}", batch.getPublicId(), dbDown.getClass().getSimpleName());
        }
    }

    /**
     * The commit's row loop. Exceptions from the persister or the result store are captured
     * rather than thrown through the parser (which would report them as an unreadable file):
     * the loop stops, and {@link #commit} rethrows after the parser has closed its resources.
     */
    private final class CommitSink implements RowSink {
        private final Long batchId;
        private final Map<String, String> mapping;
        private final ImportEvaluationContext ctx;
        private final ImportCounters counters;
        private final ImportWriteContext writeCtx;
        private final List<ImportRowResult> pending = new ArrayList<>(ImportResultStore.CHUNK_SIZE);
        private SheetHeader header;
        RuntimeException failure;

        CommitSink(Long batchId, Map<String, String> mapping, ImportEvaluationContext ctx, ImportCounters counters, ImportWriteContext writeCtx) {
            this.batchId = batchId;
            this.mapping = mapping;
            this.ctx = ctx;
            this.counters = counters;
            this.writeCtx = writeCtx;
        }

        @Override
        public void header(SheetHeader h) {
            header = h;
        }

        @Override
        public boolean row(ParsedRow row) {
            try {
                RowEvaluation e = importer.evaluate(row, header, mapping, ctx);
                ImportWriteResult w = persister.persist(e, writeCtx); // pass-through for non-success states
                pending.add(toResult(row, e, w));
                counters.count(w.state());
                if (pending.size() >= ImportResultStore.CHUNK_SIZE) flushPending();
                return true;
            } catch (RuntimeException infra) {
                failure = infra;
                return false;
            }
        }

        void flushPending() {
            if (pending.isEmpty()) return;
            resultStore.saveChunk(pending);
            pending.clear();
            batchStore.heartbeat(writeCtx.hospitalId(), batchId, counters.snapshot(), now());
        }

        private ImportRowResult toResult(ParsedRow row, RowEvaluation e, ImportWriteResult w) {
            ImportRowResult r = new ImportRowResult(batchId, e.rowNum(), w.state(), w.reasonCode());
            r.setColumnName(e.column());
            r.setMessage(w.isSuccess() ? null : (w.message() != null ? w.message() : e.message()));
            r.setPhoneMasked(e.phoneMasked());
            r.setMatchedPatientId(w.patientId() != null ? w.patientId() : e.matchedPatientId());
            if (w.state() == ImportRowState.NEEDS_REVIEW || w.state() == ImportRowState.FAILED) {
                r.setRawRowJson(rawRowJson(row, header));
            }
            return r;
        }
    }

    // ── serialisation ───────────────────────────────────────────────────────

    private String mappingJson(Map<String, String> mapping) {
        try {
            return json.writeValueAsString(new LinkedHashMap<>(mapping));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the field mapping", e);
        }
    }

    /**
     * The row as the administrator typed it, keyed by display header, for the correction loop.
     * Kept only for NEEDS_REVIEW / FAILED rows. Columns whose name suggests a credential are left
     * out regardless of content — a legacy export's "password" column is never retained.
     * Everything else in a legacy file is, by definition, patient data: this JSON is PII at rest
     * and is subject to the 90-day retention rule (a later phase).
     */
    String rawRowJson(ParsedRow row, SheetHeader header) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> display = header.display();
        for (int i = 0; i < display.size() && i < row.values().size(); i++) {
            if (isProhibitedColumn(display.get(i))) continue;
            out.put(display.get(i), row.values().get(i));
        }
        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the row for review", e);
        }
    }

    static boolean isProhibitedColumn(String displayHeader) {
        String n = displayHeader.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
        return n.contains("password") || n.contains("passwd") || n.contains("token") || n.contains("secret")
                || n.contains("authorization") || n.contains("duplicatephoneack");
    }
}
