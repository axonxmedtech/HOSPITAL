package com.hms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hms.entity.Hospital;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.repository.HospitalRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.service.hospital.PatientRegistrar;
import com.hms.service.import_.AlreadyImportedException;
import com.hms.service.import_.ImportBatchStore;
import com.hms.service.import_.ImportCommitRequest;
import com.hms.service.import_.ImportCommitSummary;
import com.hms.service.import_.ImportEngine;
import com.hms.service.import_.ImportFormat;
import com.hms.service.import_.ImportPreview;
import com.hms.service.import_.ImportResultStore;
import com.hms.service.import_.ImportRunFailedException;
import com.hms.service.import_.SpooledUpload;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole pipeline — parser → evaluator → row persister → batch/result stores — against a real
 * MySQL with V21, V22, V23 and V24 in force. What only this can show: that counters equal the
 * persisted results, that a commit is one batch even when two start at once, that an
 * infrastructure failure stops the run and leaves the batch FAILED with rows already written
 * intact, and that a preview leaves every table exactly as it found it.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ImportEngineIT.PinnedClock.class)
class ImportEngineIT {

    /** A movable clock so the 30-minute stale rule is tested to the second. */
    static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-09-18T06:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class PinnedClock {
        static final MutableClock CLOCK = new MutableClock();
        @Bean @Primary Clock importTestClock() { return CLOCK; }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("hms.it.mysql.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("hms.it.mysql.username", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("hms.it.mysql.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("hms.migrations.enabled", () -> "true");
        registry.add("spring.cache.type", () -> "simple");
    }

    @Autowired ImportEngine engine;
    @Autowired ImportBatchStore batchStore;
    @Autowired ImportBatchRepository batches;
    @Autowired HospitalRepository hospitals;
    @Autowired PatientRegistrar registrar;
    @Autowired JdbcTemplate jdbc;

    private static final Map<String, String> MAPPING = Map.of("MRN", "legacyId", "Name", "name", "Phone", "phone", "Gender", "gender", "DOB", "dateOfBirth", "Address", "address");
    private static final String HEADER = "MRN,Name,Phone,Gender,DOB,Address\n";

    private long hospitalA;
    private long hospitalB;
    private final AtomicInteger seq = new AtomicInteger((int) (System.nanoTime() % 100_000));

    @BeforeAll
    void schema() throws Exception {
        for (String t : List.of("patient_import_links", "import_row_results", "import_batches")) jdbc.execute("DROP TABLE IF EXISTS " + t);
        for (String f : List.of("V22__create_import_batches.sql", "V23__create_patient_import_links.sql", "V24__add_import_batch_active_uniqueness.sql")) {
            String sql = new String(new org.springframework.core.io.ClassPathResource("db/migration/" + f).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String st : sql.replaceAll("(?m)^\\s*--.*$", "").split(";")) if (!st.isBlank()) jdbc.execute(st);
        }
        hospitalA = hospital();
        hospitalB = hospital();
    }

    @AfterAll
    void drop() {
        for (String t : List.of("patient_import_links", "import_row_results", "import_batches")) jdbc.execute("DROP TABLE IF EXISTS " + t);
    }

    private long hospital() {
        Hospital h = new Hospital();
        h.setName("H-" + System.nanoTime());
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        return hospitals.save(h).getId();
    }

    /** Unique-per-test phone/MRN prefix so files never collide across tests. */
    private String tag() { return String.format("%05d", seq.incrementAndGet() % 100_000); }

    private String row(String mrn, String name, String phone, String extra) {
        return mrn + "," + name + "," + phone + ",F,1990-01-01," + extra + "\n";
    }

    private SpooledUpload upload(String csv) throws Exception {
        return SpooledUpload.spool(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private ImportCommitRequest request(long hospitalId) {
        return new ImportCommitRequest(hospitalId, "admin@example.test", "legacy.csv", null, MAPPING);
    }

    private ImportCommitSummary commit(long hospitalId, String csv) throws Exception {
        try (SpooledUpload u = upload(csv)) {
            return engine.commit(u, ImportFormat.CSV, request(hospitalId));
        }
    }

    private ImportPreview preview(long hospitalId, String csv) throws Exception {
        try (SpooledUpload u = upload(csv)) {
            return engine.preview(u, ImportFormat.CSV, null, MAPPING, hospitalId);
        }
    }

    private Map<String, Long> tableCounts() {
        return Map.of(
                "patients", jdbc.queryForObject("SELECT COUNT(*) FROM patients", Long.class),
                "links", jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links", Long.class),
                "batches", jdbc.queryForObject("SELECT COUNT(*) FROM import_batches", Long.class),
                "results", jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results", Long.class));
    }

    private ImportBatch batch(String publicId) {
        return batches.findByPublicIdAndHospitalId(publicId, hospitalA).or(() -> batches.findByPublicIdAndHospitalId(publicId, hospitalB)).orElseThrow();
    }

    /** Invariant: persisted counters equal the GROUP BY of persisted results. */
    private void assertCountersMatchResults(String publicId) {
        ImportBatch b = batch(publicId);
        Map<String, Long> byState = new java.util.HashMap<>();
        jdbc.query("SELECT state, COUNT(*) n FROM import_row_results WHERE batch_id = ? GROUP BY state", rs -> { byState.put(rs.getString(1), rs.getLong(2)); }, b.getId());
        assertThat((long) b.getCreatedCount()).isEqualTo(byState.getOrDefault("CREATED", 0L));
        assertThat((long) b.getUpdatedCount()).isEqualTo(byState.getOrDefault("UPDATED", 0L));
        assertThat((long) b.getSkippedCount()).isEqualTo(byState.getOrDefault("SKIPPED", 0L));
        assertThat((long) b.getNeedsReviewCount()).isEqualTo(byState.getOrDefault("NEEDS_REVIEW", 0L));
        assertThat((long) b.getFailedCount()).isEqualTo(byState.getOrDefault("FAILED", 0L));
        assertThat((long) b.getTotalRows()).isEqualTo(byState.values().stream().mapToLong(Long::longValue).sum());
    }

    // ── scenarios ────────────────────────────────────────────────────────────

    @Test
    void aThreeRowCommitCreatesUpdatesAndSkipsWithExactCountersAndAtomicLineage() throws Exception {
        String t = tag();
        String p1 = "98" + t + "001", p2 = "98" + t + "002";
        String first = HEADER + row("M" + t + "-1", "Person One", p1, "A1") + row("M" + t + "-2", "Person Two", p2, "A2");
        ImportCommitSummary s1 = commit(hospitalA, first);
        assertThat(s1.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(s1.counts().created()).isEqualTo(2);

        // Second file: row 1 unchanged (skip), row 2 new address (update), row 3 new patient (create).
        String second = HEADER + row("M" + t + "-1", "Person One", p1, "A1") + row("M" + t + "-2", "Person Two", p2, "A2-new") + row("M" + t + "-3", "Person Three", "98" + t + "003", "A3");
        ImportCommitSummary s2 = commit(hospitalA, second);

        assertThat(s2.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(s2.counts().total()).isEqualTo(3);
        assertThat(s2.counts().created()).isEqualTo(1);
        assertThat(s2.counts().updated()).isEqualTo(1);
        assertThat(s2.counts().skipped()).isEqualTo(1);
        assertThat(s2.committedAt()).isEqualTo(LocalDateTime.ofInstant(PinnedClock.CLOCK.now, PinnedClock.CLOCK.getZone()));
        assertThat(jdbc.queryForObject("SELECT address FROM patients WHERE phone = ?", String.class, p2)).isEqualTo("A2-new");
        ImportBatch b = batch(s2.batchPublicId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links WHERE last_batch_id = ?", Long.class, b.getId())).isEqualTo(2); // update + create
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links WHERE created_by_batch_id = ?", Long.class, b.getId())).isEqualTo(1);
        assertThat(b.getMappingJson()).contains("\"MRN\":\"legacyId\"");
        assertThat(b.getFileSha256()).hasSize(64);
        assertThat(b.getCreatedBy()).isEqualTo("admin@example.test");
        assertThat(b.getHeartbeatAt()).isEqualTo(b.getCommittedAt());
        assertCountersMatchResults(s2.batchPublicId());
        // raw rows: never for successes
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results WHERE batch_id = ? AND raw_row_json IS NOT NULL", Long.class, b.getId())).isZero();
    }

    @Test
    void previewOfAnyFileWritesNothingAndPreviewEqualsCommitClassification() throws Exception {
        String t = tag();
        String csv = HEADER + row("M" + t + "-1", "Person One", "98" + t + "101", "A") + row("M" + t + "-2", "Person Two", "", "A") + row("M" + t + "-1", "Dup Mrn", "98" + t + "103", "A");
        Map<String, Long> before = tableCounts();

        ImportPreview p = preview(hospitalA, csv);

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(p.counts().created()).isEqualTo(1);
        assertThat(p.counts().needsReview()).isEqualTo(1); // blank phone
        assertThat(p.counts().skipped()).isEqualTo(1); // duplicate MRN in file
        assertThat(p.samples()).extracting(ImportPreview.RowSample::reasonCode).containsExactly(ImportReasonCode.PHONE_MISSING, ImportReasonCode.DUPLICATE_MRN_IN_FILE);
        assertThat(p.previousImport()).isNull();

        ImportCommitSummary s = commit(hospitalA, csv);
        assertThat(s.counts()).isEqualTo(p.counts()); // same ordering, same context rules, no race in between
        assertThat(s.status()).isEqualTo(ImportStatus.PARTIAL);

        ImportPreview again = preview(hospitalA, csv);
        assertThat(again.previousImport()).isNotNull();
        assertThat(again.previousImport().batchPublicId()).isEqualTo(s.batchPublicId());
        assertThat(again.previousImport().status()).isEqualTo(ImportStatus.PARTIAL);
    }

    @Test
    void theSameCompletedOrPartialFileIsRefusedButFailedAndUndoneAreRetryable() throws Exception {
        String t = tag();
        String csv = HEADER + row("M" + t + "-1", "Person One", "98" + t + "201", "A");
        ImportCommitSummary done = commit(hospitalA, csv);
        Map<String, Long> before = tableCounts();

        assertThatThrownBy(() -> commit(hospitalA, csv))
                .isInstanceOf(AlreadyImportedException.class)
                .satisfies(e -> {
                    AlreadyImportedException a = (AlreadyImportedException) e;
                    assertThat(a.getCondition()).isEqualTo(AlreadyImportedException.Condition.ALREADY_IMPORTED);
                    assertThat(a.isDetailsAvailable()).isTrue();
                    assertThat(a.getBatchPublicId()).contains(done.batchPublicId());
                    assertThat(a.getStatus()).isEqualTo(ImportStatus.COMPLETED);
                    assertThat(a.getCommittedAt()).isPresent();
                });
        assertThat(tableCounts()).isEqualTo(before);

        ImportBatch b = batch(done.batchPublicId());
        jdbc.update("UPDATE import_batches SET status = 'PARTIAL' WHERE id = ?", b.getId());
        assertThatThrownBy(() -> commit(hospitalA, csv)).isInstanceOf(AlreadyImportedException.class);

        jdbc.update("UPDATE import_batches SET status = 'FAILED' WHERE id = ?", b.getId());
        ImportCommitSummary retry = commit(hospitalA, csv); // allowed; the row now matches by MRN
        assertThat(retry.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(retry.counts().skipped()).isEqualTo(1);

        jdbc.update("UPDATE import_batches SET status = 'UNDONE' WHERE public_id = ?", retry.batchPublicId());
        assertThat(commit(hospitalA, csv).status()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void theSameFileInAnotherHospitalIsIndependent() throws Exception {
        String t = tag();
        String csv = HEADER + row("M" + t + "-1", "Person One", "98" + t + "301", "A");
        commit(hospitalA, csv);

        ImportCommitSummary other = commit(hospitalB, csv);

        assertThat(other.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(other.counts().created()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone = ?", Long.class, "98" + t + "301")).isEqualTo(2);
    }

    @Test
    void twoSimultaneousCommitsOfOneFileProduceExactlyOneBatch() throws Exception {
        String t = tag();
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 40; i++) sb.append(row("M" + t + "-" + i, "Person " + i, "97" + t + String.format("%03d", i), "A"));
        String csv = sb.toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Object> a = pool.submit(() -> { go.await(); try { return commit(hospitalA, csv); } catch (AlreadyImportedException e) { return e; } });
            Future<Object> b = pool.submit(() -> { go.await(); try { return commit(hospitalA, csv); } catch (AlreadyImportedException e) { return e; } });
            go.countDown();
            Object ra = a.get(60, TimeUnit.SECONDS), rb = b.get(60, TimeUnit.SECONDS);

            List<Object> results = List.of(ra, rb);
            assertThat(results).filteredOn(r -> r instanceof ImportCommitSummary).hasSize(1);
            assertThat(results).filteredOn(r -> r instanceof AlreadyImportedException).hasSize(1);
            ImportCommitSummary winner = (ImportCommitSummary) results.stream().filter(r -> r instanceof ImportCommitSummary).findFirst().orElseThrow();
            assertThat(winner.counts().created()).isEqualTo(40);
            AlreadyImportedException loser = (AlreadyImportedException) results.stream().filter(r -> r instanceof AlreadyImportedException).findFirst().orElseThrow();
            if (loser.isDetailsAvailable()) {
                assertThat(loser.getCondition()).isEqualTo(AlreadyImportedException.Condition.ALREADY_IMPORTED);
                assertThat(loser.getBatchPublicId()).contains(winner.batchPublicId()); // the real winner, never a made-up id
            } else {
                assertThat(loser.getCondition()).isEqualTo(AlreadyImportedException.Condition.IMPORT_ALREADY_IN_PROGRESS);
                assertThat(loser.getBatchPublicId()).isEmpty();
                assertThat(loser.getCommittedAt()).isEmpty();
                assertThat(loser.getStatus()).isEqualTo(ImportStatus.RUNNING);
            }
            String sha;
            try (SpooledUpload u = upload(csv)) { sha = u.sha256(); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_batches WHERE hospital_id = ? AND file_sha256 = ?", Long.class, hospitalA, sha)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE hospital_id = ? AND phone LIKE ?", Long.class, hospitalA, "97" + t + "%")).isEqualTo(40);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void fiveHundredAndOneRowsCrossTheChunkBoundaryWithExactCountsAndAdvancingHeartbeat() throws Exception {
        String t = tag();
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < ImportResultStore.CHUNK_SIZE + 1; i++) {
            // every 57th row (starting at file row 57) has an unreadable phone → review
            String phone = (i + 2) % 57 == 0 ? "N/A" : "96" + t + String.format("%03d", i);
            sb.append(row("M" + t + "-" + i, "Person " + i, phone, "A"));
        }
        Instant start = PinnedClock.CLOCK.now;
        PinnedClock.CLOCK.now = start.plus(Duration.ofMinutes(1));

        ImportCommitSummary s = commit(hospitalA, sb.toString());

        assertThat(s.counts().total()).isEqualTo(501);
        int expectedReview = 0;
        for (int i = 0; i < 501; i++) if ((i + 2) % 57 == 0) expectedReview++;
        assertThat(s.counts().needsReview()).isEqualTo(expectedReview);
        assertThat(s.counts().created()).isEqualTo(501 - expectedReview);
        assertThat(s.status()).isEqualTo(ImportStatus.PARTIAL); // row 57 etc. did not stop the run
        assertCountersMatchResults(s.batchPublicId());
        ImportBatch b = batch(s.batchPublicId());
        assertThat(b.getHeartbeatAt()).isAfter(LocalDateTime.ofInstant(start, PinnedClock.CLOCK.getZone()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results WHERE batch_id = ?", Long.class, b.getId())).isEqualTo(501);
        // raw rows only for review, with the display headers and no prohibited columns
        List<String> raws = jdbc.queryForList("SELECT raw_row_json FROM import_row_results WHERE batch_id = ? AND state = 'NEEDS_REVIEW'", String.class, b.getId());
        assertThat(raws).hasSize(expectedReview).allSatisfy(r -> assertThat(r).contains("\"MRN\":").contains("\"Phone\":\"N/A\"").doesNotContain("password"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results WHERE batch_id = ? AND state = 'CREATED' AND raw_row_json IS NOT NULL", Long.class, b.getId())).isZero();
    }

    @Test
    void aCreateThatLosesThePhoneRaceCountsAsReviewNotCreated() throws Exception {
        String t = tag();
        String phone = "95" + t + "401";
        // Someone registers the number by hand between preview and commit.
        com.hms.entity.Patient rival = new com.hms.entity.Patient();
        rival.setHospitalId(hospitalA);
        rival.setName("Rival");
        rival.setPhone(phone);
        rival.setGender("MALE");
        rival.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        rival.setAddress("x");
        rival.setIsActive(true);
        // The evaluator would see the rival and say review; to exercise the WRITE-time race, the
        // rival is inserted after evaluation would have run — simulated by a trigger-free approach:
        // use a batch whose evaluation happens with the rival absent is not possible in one call,
        // so this scenario asserts the persister classification end-to-end via ImportedPatientWriterIT
        // and here asserts the evaluator-level classification is counted, not created.
        registrar.persistNewPatient(rival);

        ImportCommitSummary s = commit(hospitalA, HEADER + row("M" + t + "-1", "Import Person", phone, "A"));

        assertThat(s.counts().created()).isZero();
        assertThat(s.counts().needsReview()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason_code FROM import_row_results WHERE batch_id = (SELECT id FROM import_batches WHERE public_id = ?)", String.class, s.batchPublicId()))
                .isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW.name());
        assertCountersMatchResults(s.batchPublicId());
    }

    @Test
    void anInfrastructureFailureMidRunStopsProcessingAndMarksTheBatchFailed() throws Exception {
        String t = tag();
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 10; i++) sb.append(row("M" + t + "-" + i, "Person " + i, "94" + t + String.format("%03d", i), "A"));
        // Row index 5 (file row 7) hits a trigger that raises a non-constraint SQL error on the patient INSERT.
        jdbc.execute("CREATE TRIGGER it_break_insert BEFORE INSERT ON patients FOR EACH ROW "
                + "BEGIN IF NEW.phone = '94" + t + "005' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'infrastructure'; END IF; END");
        try {
            assertThatThrownBy(() -> commit(hospitalA, sb.toString())).isInstanceOf(ImportRunFailedException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS it_break_insert");
        }

        ImportBatch b = batches.findByHospitalIdOrderByCreatedAtDesc(hospitalA).get(0);
        assertThat(b.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(b.getFailureReason()).isEqualTo(ImportBatchStore.PROCESSING_FAILED_REASON);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone LIKE ?", Long.class, "94" + t + "%")).isEqualTo(5); // rows before the failure stand
        assertThat(b.getCreatedCount()).isEqualTo(5); // counters reflect what was written
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone LIKE ? AND phone >= ?", Long.class, "94" + t + "%", "94" + t + "005")).isZero(); // nothing at or after the failing row
        // The results of the first five were pending in the chunk when the run died: recorded on the
        // batch's counters but not as rows — the lineage names the batch, which is how recovery finds them.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links WHERE created_by_batch_id = ?", Long.class, b.getId())).isEqualTo(5);
    }

    @Test
    void aResultPersistenceFailureAfterASuccessfulWriteStopsTheBatchAndNeverRewritesThePatient() throws Exception {
        String t = tag();
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < ImportResultStore.CHUNK_SIZE + 5; i++) sb.append(row("M" + t + "-" + i, "Person " + i, "93" + t + String.format("%03d", i), "A"));
        jdbc.execute("CREATE TRIGGER it_break_results BEFORE INSERT ON import_row_results FOR EACH ROW "
                + "BEGIN IF NEW.row_num = 2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'results unavailable'; END IF; END");
        try {
            assertThatThrownBy(() -> commit(hospitalA, sb.toString())).isInstanceOf(ImportRunFailedException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS it_break_results");
        }

        ImportBatch b = batches.findByHospitalIdOrderByCreatedAtDesc(hospitalA).get(0);
        assertThat(b.getStatus()).isEqualTo(ImportStatus.FAILED);
        // exactly one chunk of patients was written, once each, and nothing beyond it
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone LIKE ?", Long.class, "93" + t + "%")).isEqualTo(ImportResultStore.CHUNK_SIZE);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT phone) FROM patients WHERE phone LIKE ?", Long.class, "93" + t + "%")).isEqualTo(ImportResultStore.CHUNK_SIZE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results WHERE batch_id = ?", Long.class, b.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links WHERE created_by_batch_id = ?", Long.class, b.getId())).isEqualTo(ImportResultStore.CHUNK_SIZE);
        // A FAILED batch is retryable, and the retry updates by MRN instead of creating again.
        ImportCommitSummary retry = commit(hospitalA, sb.toString());
        assertThat(retry.counts().created()).isEqualTo(5);
        assertThat(retry.counts().skipped()).isEqualTo(ImportResultStore.CHUNK_SIZE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone LIKE ?", Long.class, "93" + t + "%")).isEqualTo(ImportResultStore.CHUNK_SIZE + 5);
    }

    @Test
    void staleRunningBatchesBecomeFailedAtExactlyThirtyMinutesAndCountersAreRecomputed() throws Exception {
        String t = tag();
        String csv = HEADER + row("M" + t + "-1", "Person One", "92" + t + "501", "A");
        Instant t0 = PinnedClock.CLOCK.now;
        // Simulate an abandoned run: a RUNNING batch with two persisted results and a heartbeat at t0.
        String sha;
        try (SpooledUpload u = upload(csv)) { sha = u.sha256(); }
        // Use the production timestamp binding path: raw JDBC LocalDateTime and Hibernate's
        // Timestamp binding differ when the JVM zone is UTC but Connector/J uses Asia/Kolkata.
        LocalDateTime heartbeat = LocalDateTime.ofInstant(t0, PinnedClock.CLOCK.getZone());
        ImportBatch abandoned = batchStore.start(request(hospitalA), sha, "{}", heartbeat);
        long staleId = abandoned.getId();
        assertThat(batch(abandoned.getPublicId()).getHeartbeatAt()).isEqualTo(heartbeat);
        jdbc.update("INSERT INTO import_row_results (batch_id, row_num, state) VALUES (?,2,'CREATED'),(?,3,'NEEDS_REVIEW')", staleId, staleId);

        PinnedClock.CLOCK.now = t0.plus(Duration.ofMinutes(29)).plusSeconds(59);
        assertThat(batchStore.resolveStale(hospitalA, LocalDateTime.now(PinnedClock.CLOCK))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM import_batches WHERE id = ?", String.class, staleId)).isEqualTo("RUNNING");
        assertThatThrownBy(() -> commit(hospitalA, csv)).isInstanceOf(AlreadyImportedException.class); // still in progress

        PinnedClock.CLOCK.now = t0.plus(Duration.ofMinutes(30)).plusSeconds(1);
        ImportCommitSummary s = commit(hospitalA, csv); // resolves the stale batch first, then runs

        assertThat(s.status()).isEqualTo(ImportStatus.COMPLETED);
        Map<String, Object> stale = jdbc.queryForMap("SELECT * FROM import_batches WHERE id = ?", staleId);
        assertThat(stale.get("status")).isEqualTo("FAILED");
        assertThat(stale.get("failure_reason")).isEqualTo(ImportBatchStore.ABANDONED_REASON);
        assertThat(stale.get("created_count")).isEqualTo(1);
        assertThat(stale.get("needs_review_count")).isEqualTo(1);
        assertThat(stale.get("total_rows")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_row_results WHERE batch_id = ?", Long.class, staleId)).isEqualTo(2); // nothing deleted
    }

    @Test
    void batchLookupsAndFingerprintsAreTenantScoped() throws Exception {
        String t = tag();
        String csv = HEADER + row("M" + t + "-1", "Person One", "91" + t + "601", "A");
        ImportCommitSummary a = commit(hospitalA, csv);

        assertThat(batches.findByPublicIdAndHospitalId(a.batchPublicId(), hospitalB)).isEmpty();
        String sha;
        try (SpooledUpload u = upload(csv)) { sha = u.sha256(); }
        assertThat(batchStore.findLive(hospitalB, sha)).isEmpty();
        assertThat(batchStore.findLive(hospitalA, sha)).isPresent();
        assertThat(preview(hospitalB, csv).previousImport()).isNull();
    }
}
