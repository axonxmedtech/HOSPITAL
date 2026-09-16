package com.hms.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Legacy patient import, phase 1 — the schema foundation, against a real MySQL.
 *
 * <p>Flyway V22/V23 are plain DDL whose whole value is in constraints MySQL enforces: the unique
 * keys that make MRN matching deterministic, and the ON DELETE CASCADE rules that let the existing
 * tenant purge remove import history without a code change. None of that is observable on H2 or
 * through mocks, so the migration files themselves are applied here, verbatim, and the resulting
 * behaviour is asserted.
 *
 * <p>The application boots with {@code ddl-auto=create-drop}, so Hibernate first builds the three
 * import tables from the entities. Those are dropped and rebuilt from the migration SQL so the
 * test exercises what staging will run, not what Hibernate inferred. {@code hms.migrations.enabled}
 * is on so {@code DatabaseMigrationRunner} installs V21's generated column and unique index on
 * {@code patients}, which lets the last test prove V22/V23 leave it untouched.
 *
 * <p>Run with the same explicit datasource the other ITs use:
 * <pre>-Dhms.it.mysql.url=… -Dhms.it.mysql.username=… -Dhms.it.mysql.password=…</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportSchemaFoundationIT {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;

    private static final List<String> IMPORT_TABLES =
            List.of("patient_import_links", "import_row_results", "import_batches");

    private long hospitalA;
    private long hospitalB;

    @BeforeAll
    void applyMigrationsVerbatim() throws Exception {
        dropImportTables();
        for (String file : List.of("V22__create_import_batches.sql", "V23__create_patient_import_links.sql")) {
            for (String statement : statementsOf(file)) {
                jdbc.execute(statement);
            }
        }
        // Idempotency: staging can arrive here with the tables already present (see V12/V13).
        for (String file : List.of("V22__create_import_batches.sql", "V23__create_patient_import_links.sql")) {
            for (String statement : statementsOf(file)) {
                jdbc.execute(statement);
            }
        }
        hospitalA = seedHospital();
        hospitalB = seedHospital();
    }

    @AfterAll
    void dropInOrderSoCreateDropCanFinish() {
        dropImportTables();
    }

    // ── #4 objects ──────────────────────────────────────────────────────────

    @Test
    void v22AndV23CreateTheExpectedTablesColumnsIndexesAndForeignKeys() {
        assertThat(columnsOf("import_batches")).containsExactlyInAnyOrder(
                "id", "public_id", "hospital_id", "entity_type", "source_filename", "sheet_name",
                "mapping_json", "file_sha256", "status", "failure_reason", "total_rows", "created_count",
                "updated_count", "skipped_count", "needs_review_count", "failed_count", "created_by",
                "created_at", "heartbeat_at", "committed_at", "undone_at");
        assertThat(columnsOf("import_row_results")).containsExactlyInAnyOrder(
                "id", "batch_id", "row_num", "state", "reason_code", "column_name", "message",
                "phone_masked", "matched_patient_id", "raw_row_json");
        assertThat(columnsOf("patient_import_links")).containsExactlyInAnyOrder(
                "id", "patient_id", "hospital_id", "legacy_id", "created_by_batch_id", "last_batch_id",
                "last_imported_at", "last_imported_values_json", "custom_fields_json");

        assertThat(uniqueIndexes("import_batches")).containsEntry("uk_import_batch_public_id", "public_id");
        assertThat(nonUniqueIndexes("import_batches"))
                .containsEntry("idx_import_batch_hospital_status", "hospital_id,status")
                .containsEntry("idx_import_batch_hospital_sha", "hospital_id,file_sha256,created_at");
        assertThat(nonUniqueIndexes("import_row_results"))
                .containsEntry("idx_import_row_result_batch_row", "batch_id,row_num");
        assertThat(uniqueIndexes("patient_import_links"))
                .containsEntry("uk_patient_import_link_patient", "patient_id")
                .containsEntry("uk_patient_import_link_legacy", "hospital_id,legacy_id");

        assertThat(foreignKeys()).containsExactlyInAnyOrder(
                "import_batches:FK_import_batch_hospital->hospitals:CASCADE",
                "import_row_results:FK_import_row_result_batch->import_batches:CASCADE",
                "patient_import_links:FK_patient_import_link_patient->patients:CASCADE");
    }

    // ── #5 / #6 / #7 legacy_id semantics ─────────────────────────────────────

    @Test
    void severalLinksWithNoLegacyIdMayCoexistInOneHospital() {
        long batch = seedBatch(hospitalA);
        long p1 = seedPatient(hospitalA);
        long p2 = seedPatient(hospitalA);

        seedLink(p1, hospitalA, null, batch);
        seedLink(p2, hospitalA, null, batch);

        assertThat(count("patient_import_links", "hospital_id = ? AND legacy_id IS NULL", hospitalA))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void aLegacyIdIsUniqueWithinAHospital() {
        long batch = seedBatch(hospitalA);
        String mrn = "MRN-" + uniq();
        seedLink(seedPatient(hospitalA), hospitalA, mrn, batch);

        assertThatThrownBy(() -> seedLink(seedPatient(hospitalA), hospitalA, mrn, batch))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_patient_import_link_legacy");
    }

    @Test
    void aPatientHasAtMostOneLink() {
        long batch = seedBatch(hospitalA);
        long patient = seedPatient(hospitalA);
        seedLink(patient, hospitalA, "MRN-" + uniq(), batch);

        assertThatThrownBy(() -> seedLink(patient, hospitalA, "MRN-" + uniq(), batch))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_patient_import_link_patient");
    }

    @Test
    void theSameLegacyIdIsIndependentAcrossHospitals() {
        String mrn = "MRN-" + uniq();
        seedLink(seedPatient(hospitalA), hospitalA, mrn, seedBatch(hospitalA));
        seedLink(seedPatient(hospitalB), hospitalB, mrn, seedBatch(hospitalB));

        assertThat(count("patient_import_links", "legacy_id = ?", mrn)).isEqualTo(2);
    }

    // ── #8 / #9 cascades ─────────────────────────────────────────────────────

    @Test
    void deletingAPatientRemovesItsLink() {
        long patient = seedPatient(hospitalA);
        seedLink(patient, hospitalA, "MRN-" + uniq(), seedBatch(hospitalA));

        jdbc.update("DELETE FROM patients WHERE id = ?", patient);

        assertThat(count("patient_import_links", "patient_id = ?", patient)).isZero();
    }

    @Test
    void deletingABatchRemovesItsRowResultsButNotTheLinksThatPointAtIt() {
        long batch = seedBatch(hospitalA);
        long other = seedBatch(hospitalA);
        long patient = seedPatient(hospitalA);
        seedLink(patient, hospitalA, "MRN-" + uniq(), batch);
        jdbc.update("INSERT INTO import_row_results (batch_id, row_num, state, reason_code) VALUES (?,?,?,?)",
                batch, 2, "NEEDS_REVIEW", "PHONE_MISSING");
        jdbc.update("INSERT INTO import_row_results (batch_id, row_num, state) VALUES (?,?,?)", other, 2, "CREATED");

        jdbc.update("DELETE FROM import_batches WHERE id = ?", batch);

        assertThat(count("import_row_results", "batch_id = ?", batch)).isZero();
        assertThat(count("import_row_results", "batch_id = ?", other)).isEqualTo(1);
        // History pointer, no FK: the link survives and still names the batch that created it.
        assertThat(count("patient_import_links", "patient_id = ? AND created_by_batch_id = ?", patient, batch))
                .isEqualTo(1);
    }

    /**
     * The existing tenant purge (PlatformHospitalService.deleteHospital) deletes patients by
     * hospital and finishes with DELETE FROM hospitals. Reproduced statement-for-statement for the
     * tables involved: every import object of the purged tenant must be gone, and nothing of any
     * other tenant may be touched.
     */
    @Test
    void theExistingTenantPurgeOrderRemovesAllImportHistoryOfThatTenantOnly() {
        long purged = seedHospital();
        long purgedBatch = seedBatch(purged);
        seedLink(seedPatient(purged), purged, "MRN-" + uniq(), purgedBatch);
        jdbc.update("INSERT INTO import_row_results (batch_id, row_num, state) VALUES (?,?,?)", purgedBatch, 2, "CREATED");

        long keptBatch = seedBatch(hospitalB);
        long keptPatient = seedPatient(hospitalB);
        seedLink(keptPatient, hospitalB, "MRN-" + uniq(), keptBatch);
        jdbc.update("INSERT INTO import_row_results (batch_id, row_num, state) VALUES (?,?,?)", keptBatch, 2, "CREATED");

        jdbc.update("DELETE FROM patients WHERE hospital_id = ?", purged);
        jdbc.update("DELETE FROM hospital_modules WHERE hospital_id = ?", purged); // as the purge does, before hospitals
        jdbc.update("DELETE FROM hospitals WHERE id = ?", purged);

        assertThat(count("import_batches", "hospital_id = ?", purged)).isZero();
        assertThat(count("import_row_results", "batch_id = ?", purgedBatch)).isZero();
        assertThat(count("patient_import_links", "hospital_id = ?", purged)).isZero();

        assertThat(count("import_batches", "id = ?", keptBatch)).isEqualTo(1);
        assertThat(count("import_row_results", "batch_id = ?", keptBatch)).isEqualTo(1);
        assertThat(count("patient_import_links", "patient_id = ?", keptPatient)).isEqualTo(1);
    }

    // ── #10 / #11 patients untouched ─────────────────────────────────────────

    @Test
    void patientsTableAndTheV21PhoneInvariantAreLeftExactlyAsTheyWere() {
        Map<String, String> phone = column("patients", "phone");
        assertThat(phone).containsEntry("IS_NULLABLE", "NO").containsEntry("COLUMN_TYPE", "varchar(15)");
        Map<String, String> gender = column("patients", "gender");
        assertThat(gender).containsEntry("IS_NULLABLE", "NO");
        assertThat(columnsOf("patients")).contains(
                "duplicate_phone_ack_for", "duplicate_phone_ack_at", "duplicate_phone_ack_by", "active_phone_key");
        assertThat(column("patients", "active_phone_key").get("EXTRA")).contains("VIRTUAL GENERATED");
        assertThat(uniqueIndexes("patients")).containsEntry("uq_patient_active_phone", "hospital_id,active_phone_key");
        assertThat(columnsOf("patients")).doesNotContain(
                "legacy_id", "source", "import_batch_id", "custom_fields", "manually_edited");

        // And it still bites: a second active, unacknowledged patient on one phone is refused.
        String shared = freshPhone();
        seedPatient(hospitalA, shared);
        assertThatThrownBy(() -> seedPatient(hospitalA, shared))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_patient_active_phone");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void dropImportTables() {
        for (String table : IMPORT_TABLES) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
    }

    private static List<String> statementsOf(String migration) throws Exception {
        String sql = new String(new ClassPathResource("db/migration/" + migration).getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        // Comments first, then split: the migration headers are prose and contain semicolons.
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*$", "");
        return java.util.Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String uniq() { return Long.toString(System.nanoTime()); }

    private String freshPhone() {
        String tail = uniq();
        return "98" + tail.substring(tail.length() - 8);
    }

    /** Through the entity, as PatientDuplicatePhoneConcurrencyIT does: the table is Hibernate-built here. */
    private long seedHospital() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    private long seedPatient(long hospitalId) {
        return seedPatient(hospitalId, freshPhone());
    }

    private long seedPatient(long hospitalId, String phone) {
        Patient p = new Patient();
        p.setHospitalId(hospitalId);
        p.setName("Patient");
        p.setPhone(phone);
        p.setGender("MALE");
        p.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        p.setAddress("QA");
        p.setIsActive(true);
        return patientRepository.saveAndFlush(p).getId();
    }

    private long seedBatch(long hospitalId) {
        jdbc.update("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status, created_at)"
                        + " VALUES (?,?,?,?,?,NOW(6))",
                "b-" + uniq(), hospitalId, "PATIENT", "0".repeat(64), "COMPLETED");
        return jdbc.queryForObject("SELECT MAX(id) FROM import_batches", Long.class);
    }

    private void seedLink(long patientId, long hospitalId, String legacyId, long batchId) {
        jdbc.update("INSERT INTO patient_import_links (patient_id, hospital_id, legacy_id, created_by_batch_id,"
                        + " last_batch_id, last_imported_at) VALUES (?,?,?,?,?,NOW(6))",
                patientId, hospitalId, legacyId, batchId, batchId);
    }

    private long count(String table, String where, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, args);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ?", String.class, table);
    }

    private Map<String, String> column(String table, String name) {
        Map<String, Object> row = jdbc.queryForMap("SELECT IS_NULLABLE, COLUMN_TYPE, EXTRA FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, name);
        return Map.of("IS_NULLABLE", String.valueOf(row.get("IS_NULLABLE")),
                "COLUMN_TYPE", String.valueOf(row.get("COLUMN_TYPE")),
                "EXTRA", String.valueOf(row.get("EXTRA")));
    }

    private Map<String, String> indexes(String table, boolean unique) {
        Map<String, String> out = new java.util.HashMap<>();
        jdbc.query("SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols"
                        + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
                        + " AND NON_UNIQUE = ? GROUP BY INDEX_NAME",
                rs -> { out.put(rs.getString("INDEX_NAME"), rs.getString("cols")); },
                table, unique ? 0 : 1);
        return out;
    }

    private Map<String, String> uniqueIndexes(String table) { return indexes(table, true); }

    private Map<String, String> nonUniqueIndexes(String table) { return indexes(table, false); }

    private List<String> foreignKeys() {
        return jdbc.query("SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE"
                        + " FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME IN ('import_batches','import_row_results','patient_import_links')",
                (rs, i) -> rs.getString(1) + ":" + rs.getString(2) + "->" + rs.getString(3) + ":" + rs.getString(4));
    }
}
