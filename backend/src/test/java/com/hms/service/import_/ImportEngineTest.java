package com.hms.service.import_;

import static com.hms.service.import_.ImportTestFiles.csv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowResult;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The engine as an orchestrator, with the evaluator, persister and stores mocked: one context
 * per run, preview touches no store, commit persists only success candidates, the FINAL outcome
 * drives the counters, chunks are bounded and heartbeats ride on chunks, infrastructure failures
 * stop the loop while row-local ones do not, and nothing from the file reaches a log line.
 */
class ImportEngineTest {

    private static final long H = 7L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant T0 = Instant.parse("2026-09-18T06:30:00Z");

    private final PatientImporter importer = mock(PatientImporter.class);
    private final ImportRowPersister persister = mock(ImportRowPersister.class);
    private final ImportBatchStore batchStore = mock(ImportBatchStore.class);
    private final ImportResultStore resultStore = mock(ImportResultStore.class);
    private final Clock clock = Clock.fixed(T0, ZONE);
    private final ImportEngine engine = new ImportEngine(new WorkbookParser(), importer, persister, batchStore, resultStore, new ObjectMapper(), clock);

    private final Map<String, String> mapping = Map.of("Name", "name", "Phone", "phone");
    private static final String PII = "SECRET-NAME-VALUE";
    private static final String FILENAME = "acquisition-export-2019.csv";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ImportEngine.class);

    private ImportBatch batch;
    private final List<List<ImportRowResult>> savedChunks = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        batch = new ImportBatch();
        batch.setId(42L);
        batch.setPublicId("batch-public-id");
        batch.setHospitalId(H);
        when(batchStore.start(any(), anyString(), anyString(), any())).thenReturn(batch);
        when(batchStore.finish(anyLong(), anyLong(), any(), any())).thenAnswer(inv -> {
            ImportCounters.Snapshot c = inv.getArgument(2);
            batch.setStatus(c.needsReview() == 0 && c.failed() == 0 ? ImportStatus.COMPLETED : ImportStatus.PARTIAL);
            batch.setCommittedAt(inv.getArgument(3));
            return batch;
        });
        when(batchStore.findLive(anyLong(), anyString())).thenReturn(Optional.empty());
        // The engine reuses its pending buffer after each chunk (bounded memory), so copy at call time.
        org.mockito.Mockito.doAnswer(inv -> { savedChunks.add(new java.util.ArrayList<>(inv.<List<ImportRowResult>>getArgument(0))); return null; })
                .when(resultStore).saveChunk(any());
        // Persister passes non-success evaluations through, as the real one does.
        org.mockito.Mockito.doAnswer(inv -> {
            RowEvaluation e = inv.getArgument(0);
            if (e.state() == ImportRowState.CREATED) return ImportWriteResult.created(1000L + e.rowNum());
            if (e.state() == ImportRowState.UPDATED) return ImportWriteResult.updated(e.matchedPatientId());
            return new ImportWriteResult(e.state(), e.reasonCode(), e.matchedPatientId(), e.relatedPatientIds(), e.message());
        }).when(persister).persist(any(), any());
    }

    @AfterEach
    void noPiiInLogs() {
        logger.detachAppender(logs);
        for (ILoggingEvent e : logs.list) {
            assertThat(e.getFormattedMessage()).doesNotContain(PII).doesNotContain(FILENAME).doesNotContain("9000000001");
        }
    }

    private static RowEvaluation created(int rowNum) {
        return RowEvaluation.created(rowNum, new CreateCandidate(H, new PatientFieldValues("T", "9000000001", "MALE", null, null, null, null), null, Map.of()));
    }

    private static RowEvaluation review(int rowNum) {
        return RowEvaluation.review(rowNum, ImportReasonCode.PHONE_MISSING, "Phone", "blank phone", null, null, List.of());
    }

    private static RowEvaluation skipped(int rowNum) {
        return RowEvaluation.skipped(rowNum, ImportReasonCode.NO_CHANGE, "same", 5L);
    }

    private static String rows(int n) {
        StringBuilder sb = new StringBuilder("Name,Phone\n");
        for (int i = 0; i < n; i++) sb.append(PII).append(i).append(",9000000001\n");
        return sb.toString();
    }

    private ImportCommitRequest request() {
        return new ImportCommitRequest(H, "admin@example.test", FILENAME, null, mapping);
    }

    @Test
    void previewEvaluatesEveryRowWithOneContextAndNeverTouchesAStore() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> {
            ParsedRow r = inv.getArgument(0);
            return r.rowNum() % 2 == 0 ? created(r.rowNum()) : review(r.rowNum());
        });

        ImportPreview p;
        try (SpooledUpload u = csv(rows(6))) {
            p = engine.preview(u, ImportFormat.CSV, null, mapping, H);
        }

        assertThat(p.counts().total()).isEqualTo(6);
        assertThat(p.counts().created()).isEqualTo(3);
        assertThat(p.counts().needsReview()).isEqualTo(3);
        assertThat(p.samples()).hasSize(3).allSatisfy(s -> assertThat(s.state()).isEqualTo(ImportRowState.NEEDS_REVIEW));
        assertThat(p.headers()).containsExactly("Name", "Phone");
        ArgumentCaptor<ImportEvaluationContext> ctx = ArgumentCaptor.forClass(ImportEvaluationContext.class);
        verify(importer, times(6)).evaluate(any(), any(), any(), ctx.capture());
        assertThat(ctx.getAllValues()).allSatisfy(c -> assertThat(c).isSameAs(ctx.getAllValues().get(0)));
        assertThat(ctx.getValue().hospitalId()).isEqualTo(H);
        verifyNoInteractions(persister, resultStore);
        verify(batchStore, never()).start(any(), anyString(), anyString(), any());
        verify(batchStore, never()).heartbeat(anyLong(), anyLong(), any(), any());
        verify(batchStore, never()).finish(anyLong(), anyLong(), any(), any());
        verify(batchStore).findLive(eq(H), anyString()); // read-only: reports a previous import
    }

    @Test
    void previewSamplesAreBoundedAndTruncationIsReported() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> review(((ParsedRow) inv.getArgument(0)).rowNum()));

        ImportPreview p;
        try (SpooledUpload u = csv(rows(ImportPreview.MAX_SAMPLES + 25))) {
            p = engine.preview(u, ImportFormat.CSV, null, mapping, H);
        }

        assertThat(p.counts().needsReview()).isEqualTo(ImportPreview.MAX_SAMPLES + 25);
        assertThat(p.samples()).hasSize(ImportPreview.MAX_SAMPLES);
        assertThat(p.samplesTruncated()).isTrue();
    }

    @Test
    void previewReportsAPreviousLiveImportOfTheSameBytes() throws Exception {
        ImportBatch prev = new ImportBatch();
        prev.setPublicId("earlier");
        prev.setStatus(ImportStatus.PARTIAL);
        prev.setCommittedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        when(batchStore.findLive(eq(H), anyString())).thenReturn(Optional.of(prev));
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));

        try (SpooledUpload u = csv(rows(1))) {
            ImportPreview p = engine.preview(u, ImportFormat.CSV, null, mapping, H);
            assertThat(p.previousImport()).isNotNull();
            assertThat(p.previousImport().batchPublicId()).isEqualTo("earlier");
            assertThat(p.previousImport().status()).isEqualTo(ImportStatus.PARTIAL);
        }
    }

    @Test
    void commitPersistsOnlySuccessCandidatesAndCountsTheFinalOutcome() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> {
            int n = ((ParsedRow) inv.getArgument(0)).rowNum();
            return switch (n) {
                case 2, 3 -> created(n);
                case 4 -> skipped(n);
                default -> review(n);
            };
        });
        // Row 3's create loses the phone race at write time.
        org.mockito.Mockito.doAnswer(inv -> {
            RowEvaluation e = inv.getArgument(0);
            if (e.rowNum() == 3) return ImportWriteResult.review(ImportReasonCode.DUPLICATE_PHONE_RACE, null, List.of(9L), "race");
            if (e.state() == ImportRowState.CREATED) return ImportWriteResult.created(1000L + e.rowNum());
            return new ImportWriteResult(e.state(), e.reasonCode(), e.matchedPatientId(), e.relatedPatientIds(), e.message());
        }).when(persister).persist(any(), any());

        ImportCommitSummary s;
        try (SpooledUpload u = csv(rows(4))) {
            s = engine.commit(u, ImportFormat.CSV, request());
        }

        assertThat(s.batchPublicId()).isEqualTo("batch-public-id");
        assertThat(s.counts().total()).isEqualTo(4);
        assertThat(s.counts().created()).isEqualTo(1); // row 3 did NOT count as created
        assertThat(s.counts().needsReview()).isEqualTo(2); // row 3's race + row 5
        assertThat(s.counts().skipped()).isEqualTo(1);
        assertThat(s.status()).isEqualTo(ImportStatus.PARTIAL);
        assertThat(s.committedAt()).isEqualTo(LocalDateTime.ofInstant(T0, ZONE));

        verify(resultStore).saveChunk(any());
        List<ImportRowResult> results = savedChunks.get(0);
        assertThat(results).extracting(ImportRowResult::getState)
                .containsExactly(ImportRowState.CREATED, ImportRowState.NEEDS_REVIEW, ImportRowState.SKIPPED, ImportRowState.NEEDS_REVIEW);
        assertThat(results.get(1).getReasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
        // raw rows only where a human must look
        assertThat(results.get(0).getRawRowJson()).isNull();
        assertThat(results.get(2).getRawRowJson()).isNull();
        assertThat(results.get(1).getRawRowJson()).contains(PII + "1");
        assertThat(results.get(3).getRawRowJson()).contains(PII + "3");
        assertThat(results).allSatisfy(r -> assertThat(r.getBatchId()).isEqualTo(42L));
        // the persister saw every row once, the writable ones as candidates
        verify(persister, times(4)).persist(any(), any());
        verify(batchStore).start(any(), anyString(), contains("\"Name\":\"name\""), eq(LocalDateTime.ofInstant(T0, ZONE)));
    }

    private static String contains(String s) {
        return org.mockito.ArgumentMatchers.contains(s);
    }

    @Test
    void chunksAreBoundedAndHeartbeatsRideOnChunksNotRows() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));

        try (SpooledUpload u = csv(rows(ImportResultStore.CHUNK_SIZE * 2 + 1))) {
            engine.commit(u, ImportFormat.CSV, request());
        }

        verify(resultStore, times(3)).saveChunk(any());
        assertThat(savedChunks).extracting(List::size).containsExactly(ImportResultStore.CHUNK_SIZE, ImportResultStore.CHUNK_SIZE, 1);
        verify(batchStore, times(3)).heartbeat(eq(H), eq(42L), any(), any());
        ArgumentCaptor<ImportCounters.Snapshot> hb = ArgumentCaptor.forClass(ImportCounters.Snapshot.class);
        verify(batchStore, times(3)).heartbeat(eq(H), eq(42L), hb.capture(), any());
        assertThat(hb.getAllValues()).extracting(ImportCounters.Snapshot::created).containsExactly(500, 1000, 1001);
    }

    @Test
    void anInfrastructureFailureStopsTheLoopMarksFailedAndPropagates() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));
        org.mockito.Mockito.doAnswer(inv -> {
            RowEvaluation e = inv.getArgument(0);
            if (e.rowNum() == 4) throw new CannotAcquireLockException("db gone");
            return ImportWriteResult.created(1000L + e.rowNum());
        }).when(persister).persist(any(), any());

        try (SpooledUpload u = csv(rows(100))) {
            assertThatThrownBy(() -> engine.commit(u, ImportFormat.CSV, request()))
                    .isInstanceOf(ImportRunFailedException.class)
                    .hasCauseInstanceOf(CannotAcquireLockException.class);
        }

        verify(persister, times(3)).persist(any(), any()); // rows 2, 3, 4 — nothing after
        verify(batchStore).fail(eq(H), eq(42L), eq(ImportBatchStore.PROCESSING_FAILED_REASON), any(), any());
        verify(batchStore, never()).finish(anyLong(), anyLong(), any(), any());
        verify(resultStore, never()).saveChunk(any()); // the pending chunk is not written after a failure
    }

    @Test
    void aResultChunkFailureAfterASuccessfulPatientWriteStopsTheBatchWithoutRetrying() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));
        doThrow(new DataIntegrityViolationException("results table gone")).when(resultStore).saveChunk(any());

        try (SpooledUpload u = csv(rows(ImportResultStore.CHUNK_SIZE + 10))) {
            assertThatThrownBy(() -> engine.commit(u, ImportFormat.CSV, request())).isInstanceOf(ImportRunFailedException.class);
        }

        verify(persister, times(ImportResultStore.CHUNK_SIZE)).persist(any(), any()); // exactly one chunk of patients, none twice
        verify(resultStore, times(1)).saveChunk(any());
        verify(batchStore).fail(eq(H), eq(42L), eq(ImportBatchStore.PROCESSING_FAILED_REASON), any(), any());
    }

    @Test
    void rowLocalOutcomesNeverStopTheLoop() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));
        List<ImportReasonCode> local = List.of(ImportReasonCode.VALIDATION_FAILED, ImportReasonCode.CONSTRAINT_FAILED,
                ImportReasonCode.DUPLICATE_PHONE_RACE, ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION);
        org.mockito.Mockito.doAnswer(inv -> {
            RowEvaluation e = inv.getArgument(0);
            ImportReasonCode c = local.get((e.rowNum() - 2) % local.size());
            return c == ImportReasonCode.VALIDATION_FAILED || c == ImportReasonCode.CONSTRAINT_FAILED
                    ? ImportWriteResult.failed(c, null, "x")
                    : ImportWriteResult.review(c, null, List.of(), "x");
        }).when(persister).persist(any(), any());

        ImportCommitSummary s;
        try (SpooledUpload u = csv(rows(8))) {
            s = engine.commit(u, ImportFormat.CSV, request());
        }

        assertThat(s.counts().total()).isEqualTo(8);
        assertThat(s.counts().failed()).isEqualTo(4);
        assertThat(s.counts().needsReview()).isEqualTo(4);
        assertThat(s.status()).isEqualTo(ImportStatus.PARTIAL);
        verify(batchStore, never()).fail(anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test
    void aLiveBatchForTheSameBytesRefusesTheCommitBeforeAnyRowIsRead() throws Exception {
        when(batchStore.start(any(), anyString(), anyString(), any())).thenThrow(new DataIntegrityViolationException("uk_import_batch_active"));
        when(batchStore.alreadyImported(eq(H), anyString())).thenReturn(new AlreadyImportedException("earlier", ImportStatus.RUNNING, null));

        try (SpooledUpload u = csv(rows(3))) {
            assertThatThrownBy(() -> engine.commit(u, ImportFormat.CSV, request()))
                    .isInstanceOf(AlreadyImportedException.class)
                    .satisfies(e -> assertThat(((AlreadyImportedException) e).getBatchPublicId()).isEqualTo("earlier"));
        }
        verifyNoInteractions(importer, persister, resultStore);
        verify(batchStore).resolveStale(eq(H), any()); // stale runs are resolved before the attempt
    }

    @Test
    void skippedRowsAloneStillMakeACompletedBatch() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> {
            int n = ((ParsedRow) inv.getArgument(0)).rowNum();
            return n == 2 ? created(n) : skipped(n);
        });

        ImportCommitSummary s;
        try (SpooledUpload u = csv(rows(5))) {
            s = engine.commit(u, ImportFormat.CSV, request());
        }
        assertThat(s.counts().skipped()).isEqualTo(4);
        assertThat(s.status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void rawRowJsonOmitsCredentialLookingColumnsAndUsesDisplayHeaders() {
        SheetHeader h = new SheetHeader("csv", List.of("Name", "Password", "API Token", "Authorization", "duplicate_phone_ack_for", "Caste"));
        ParsedRow row = new ParsedRow(2, List.of("T", "hunter2", "tok", "Bearer x", "9", "X"), List.of());

        String raw = engine.rawRowJson(row, h);

        assertThat(raw).isEqualTo("{\"Name\":\"T\",\"Caste\":\"X\"}");
        assertThat(ImportEngine.isProhibitedColumn("Passwd")).isTrue();
        assertThat(ImportEngine.isProhibitedColumn("Client Secret")).isTrue();
        assertThat(ImportEngine.isProhibitedColumn("Phone")).isFalse();
    }

    @Test
    void hospitalAndActorComeOnlyFromTheRequestNeverTheFile() throws Exception {
        when(importer.evaluate(any(), any(), any(), any())).thenAnswer(inv -> created(((ParsedRow) inv.getArgument(0)).rowNum()));
        Map<String, String> m = Map.of("Name", "name", "Phone", "phone");

        try (SpooledUpload u = csv("Name,Phone,hospital_id,created_by\nT,9000000001,999,hacker@example.test\n")) {
            engine.commit(u, ImportFormat.CSV, new ImportCommitRequest(H, "admin@example.test", FILENAME, null, m));
        }

        ArgumentCaptor<ImportCommitRequest> req = ArgumentCaptor.forClass(ImportCommitRequest.class);
        verify(batchStore).start(req.capture(), anyString(), anyString(), any());
        assertThat(req.getValue().hospitalId()).isEqualTo(H);
        assertThat(req.getValue().createdBy()).isEqualTo("admin@example.test");
        ArgumentCaptor<ImportWriteContext> wc = ArgumentCaptor.forClass(ImportWriteContext.class);
        verify(persister).persist(any(), wc.capture());
        assertThat(wc.getValue().hospitalId()).isEqualTo(H);
        assertThat(wc.getValue().batchId()).isEqualTo(42L);
    }

    @Test
    void theStaleCutoffIsExactlyThirtyMinutes() {
        assertThat(ImportBatchStore.STALE_AFTER).isEqualTo(java.time.Duration.ofMinutes(30));
    }
}
