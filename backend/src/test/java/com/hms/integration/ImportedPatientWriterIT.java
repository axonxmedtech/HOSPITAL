package com.hms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.import_.CreateCandidate;
import com.hms.service.import_.ImportRowPersister;
import com.hms.service.import_.ImportTenantMismatchException;
import com.hms.service.import_.ImportWriteContext;
import com.hms.service.import_.ImportWriteResult;
import com.hms.service.import_.PatientFieldValues;
import com.hms.service.import_.UpdateCandidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The import writer's transaction boundary, against a real MySQL with the real V21 index and the
 * real V22/V23 constraints. What is asserted here cannot be seen on H2 or through mocks: that
 * patient and lineage commit or roll back together, that the active-phone index — not the
 * evaluator — is the authority on a race and that losing one leaves the target patient untouched
 * to the byte, and that a proposal computed from state a human has since changed is refused.
 *
 * <p>Runs with the same explicit datasource the other ITs use:
 * <pre>-Dhms.it.mysql.url=… -Dhms.it.mysql.username=… -Dhms.it.mysql.password=…</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportedPatientWriterIT {

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

    @Autowired ImportRowPersister persister;
    @Autowired PatientRepository patients;
    @Autowired PatientImportLinkRepository links;
    @Autowired ImportBatchRepository batches;
    @Autowired HospitalRepository hospitals;
    @Autowired JdbcTemplate jdbc;

    private long hospitalA;
    private long hospitalB;
    private long batchA;
    private long batchB;
    private long undoneBatchA;

    @BeforeAll
    void schemaAndFixtures() throws Exception {
        // Hibernate built the import tables from the entities without the V22/V23 FKs and unique
        // keys; the writer's atomicity story depends on those, so rebuild them from the migrations.
        for (String t : List.of("patient_import_links", "import_row_results", "import_batches")) jdbc.execute("DROP TABLE IF EXISTS " + t);
        for (String f : List.of("V22__create_import_batches.sql", "V23__create_patient_import_links.sql", "V24__add_import_batch_active_uniqueness.sql")) {
            String sql = new String(new org.springframework.core.io.ClassPathResource("db/migration/" + f).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String st : sql.replaceAll("(?m)^\\s*--.*$", "").split(";")) if (!st.isBlank()) jdbc.execute(st);
        }
        hospitalA = hospital();
        hospitalB = hospital();
        batchA = batch(hospitalA, ImportStatus.RUNNING);
        batchB = batch(hospitalB, ImportStatus.RUNNING);
        undoneBatchA = batch(hospitalA, ImportStatus.UNDONE);
    }

    @AfterAll
    void dropSoCreateDropCanFinish() {
        for (String t : List.of("patient_import_links", "import_row_results", "import_batches")) jdbc.execute("DROP TABLE IF EXISTS " + t);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private String uniq() { return Long.toString(System.nanoTime()); }

    private String freshPhone() {
        String t = uniq();
        return "97" + t.substring(t.length() - 8);
    }

    private long hospital() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        return hospitals.save(h).getId();
    }

    private long batch(long hospitalId, ImportStatus status) {
        ImportBatch b = new ImportBatch();
        b.setHospitalId(hospitalId);
        String u = uniq();
        b.setFileSha256(("0".repeat(64) + u).substring(u.length())); // V24: one live batch per file
        b.setStatus(status);
        return batches.save(b).getId();
    }

    private ImportWriteContext ctx(long hospitalId, long batchId) {
        return new ImportWriteContext(hospitalId, batchId, LocalDateTime.of(2026, 9, 18, 12, 0));
    }

    private static PatientFieldValues values(String name, String phone, String address) {
        return new PatientFieldValues(name, phone, "FEMALE", LocalDate.of(1988, 5, 6), null, address, null);
    }

    private CreateCandidate create(long hospitalId, String phone, String legacyId) {
        return new CreateCandidate(hospitalId, values("Import Person", phone, "Addr"), legacyId, Map.of("Caste", "X"));
    }

    /** A patient registered by hand (no link), as reception would. */
    private Patient manual(long hospitalId, String phone) {
        Patient p = new Patient();
        p.setHospitalId(hospitalId);
        p.setName("Manual Person");
        p.setPhone(phone);
        p.setGender("MALE");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setAddress("Manual Addr");
        p.setIsActive(true);
        return patients.saveAndFlush(p);
    }

    private Patient imported(long hospitalId, String phone, String legacyId) {
        ImportWriteResult r = persister.create(create(hospitalId, phone, legacyId), ctx(hospitalId, hospitalId == hospitalA ? batchA : batchB));
        assertThat(r.state()).isEqualTo(ImportRowState.CREATED);
        return patients.findById(r.patientId()).orElseThrow();
    }

    private UpdateCandidate update(Patient asSeen, Map<String, String> changes, boolean reactivate, boolean clearAck) {
        return new UpdateCandidate(
                asSeen.getHospitalId(), asSeen.getId(), PatientFieldValues.of(asSeen), Boolean.TRUE.equals(asSeen.getIsActive()),
                asSeen.getDuplicatePhoneAckFor(), changes, reactivate, clearAck, null, Map.of(), true);
    }

    private Map<String, Object> row(String table, String where, Object... args) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE " + where, args);
    }

    private Patient reload(long id) {
        return patients.findById(id).orElseThrow();
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void createWritesPatientAndLinkAtomicallyWithCurrentIdentifierBehaviourAndNoAcknowledgement() {
        String phone = freshPhone();

        ImportWriteResult r = persister.create(create(hospitalA, phone, "MRN-" + uniq()), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.CREATED);
        Patient p = reload(r.patientId());
        assertThat(p.getCustomId()).isEqualTo("PAT" + p.getId()); // registrar
        assertThat(p.getPublicId()).isNotBlank().hasSize(36); // @PrePersist
        assertThat(p.getHospitalId()).isEqualTo(hospitalA);
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getDuplicatePhoneAckFor()).isNull();
        assertThat(p.getDuplicatePhoneAckAt()).isNull();
        assertThat(p.getDuplicatePhoneAckBy()).isNull();
        Map<String, Object> link = row("patient_import_links", "patient_id = ?", p.getId());
        assertThat(link.get("hospital_id")).isEqualTo(hospitalA);
        assertThat(link.get("created_by_batch_id")).isEqualTo(batchA);
        assertThat(link.get("last_batch_id")).isEqualTo(batchA);
        assertThat((String) link.get("last_imported_values_json")).contains("\"phone\":\"" + phone + "\"").doesNotContain("publicId").doesNotContain("customId");
        assertThat((String) link.get("custom_fields_json")).isEqualTo("{\"Caste\":\"X\"}");
    }

    @Test
    void aLinkConstraintFailureRollsTheNewPatientBackToo() {
        String legacyId = "MRN-" + uniq();
        imported(hospitalA, freshPhone(), legacyId); // takes the (hospital, legacy_id) key
        long before = patients.count();
        String phone = freshPhone();

        ImportWriteResult r = persister.create(create(hospitalA, phone, legacyId), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.CONSTRAINT_FAILED); // not a phone race
        assertThat(patients.count()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE phone = ?", Long.class, phone)).isZero();
    }

    @Test
    void twoConcurrentCreatesOnOnePhoneLeaveOneActivePatientAndNoOrphanLineage() throws Exception {
        String phone = freshPhone();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<ImportWriteResult> a = pool.submit(() -> { go.await(); return persister.create(create(hospitalA, phone, "MRN-" + uniq()), ctx(hospitalA, batchA)); });
            Future<ImportWriteResult> b = pool.submit(() -> { go.await(); return persister.create(create(hospitalA, phone, "MRN-" + uniq()), ctx(hospitalA, batchA)); });
            go.countDown();
            ImportWriteResult ra = a.get(30, TimeUnit.SECONDS);
            ImportWriteResult rb = b.get(30, TimeUnit.SECONDS);

            List<ImportWriteResult> results = List.of(ra, rb);
            assertThat(results).filteredOn(r -> r.state() == ImportRowState.CREATED).hasSize(1);
            ImportWriteResult loser = results.stream().filter(r -> r.state() != ImportRowState.CREATED).findFirst().orElseThrow();
            assertThat(loser.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
            assertThat(loser.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
            long winnerId = results.stream().filter(r -> r.state() == ImportRowState.CREATED).findFirst().orElseThrow().patientId();
            assertThat(loser.relatedPatientIds()).containsExactly(winnerId);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE hospital_id = ? AND phone = ? AND is_active = 1", Long.class, hospitalA, phone)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patient_import_links l WHERE l.hospital_id = ? AND NOT EXISTS (SELECT 1 FROM patients p WHERE p.id = l.patient_id)", Long.class, hospitalA)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aCreateWhoseTenantDoesNotMatchTheContextIsRefusedAndWritesNothing() {
        long before = patients.count();
        assertThatThrownBy(() -> persister.create(create(hospitalB, freshPhone(), null), ctx(hospitalA, batchA)))
                .isInstanceOf(ImportTenantMismatchException.class);
        assertThat(patients.count()).isEqualTo(before);
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    void updateAppliesOnlyTheProposedFieldsAndAdvancesTheLineageAtomically() {
        Patient p = imported(hospitalA, freshPhone(), "MRN-" + uniq());
        long later = batch(hospitalA, ImportStatus.RUNNING);

        ImportWriteResult r = persister.update(update(p, Map.of("address", "New Addr", "email", "x@example.test"), false, false), ctx(hospitalA, later));

        assertThat(r.state()).isEqualTo(ImportRowState.UPDATED);
        Patient after = reload(p.getId());
        assertThat(after.getAddress()).isEqualTo("New Addr");
        assertThat(after.getEmail()).isEqualTo("x@example.test");
        assertThat(after.getName()).isEqualTo(p.getName());
        assertThat(after.getPhone()).isEqualTo(p.getPhone());
        assertThat(after.getCustomId()).isEqualTo(p.getCustomId());
        assertThat(after.getPublicId()).isEqualTo(p.getPublicId());
        Map<String, Object> link = row("patient_import_links", "patient_id = ?", p.getId());
        assertThat(link.get("created_by_batch_id")).isEqualTo(batchA); // preserved
        assertThat(link.get("last_batch_id")).isEqualTo(later);
        assertThat((String) link.get("last_imported_values_json")).contains("\"address\":\"New Addr\"").contains("\"email\":\"x@example.test\"");
    }

    @Test
    void aLineageFailureRollsThePatientChangeBack() {
        Patient p = imported(hospitalA, freshPhone(), "MRN-" + uniq());
        // Make the lineage write fail after the patient UPDATE has already executed in the same
        // transaction: a trigger that refuses any link update for this patient.
        jdbc.execute("CREATE TRIGGER it_refuse_link_update BEFORE UPDATE ON patient_import_links FOR EACH ROW "
                + "BEGIN IF NEW.patient_id = " + p.getId() + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'lineage refused'; END IF; END");
        try {
            assertThatThrownBy(() -> persister.update(update(p, Map.of("address", "Should Not Persist"), false, false), ctx(hospitalA, batchA)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class); // not a row-local class: propagates
            assertThat(reload(p.getId()).getAddress()).isEqualTo("Addr"); // the patient change rolled back with the link
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS it_refuse_link_update");
        }
    }

    @Test
    void aPatientEditedAfterEvaluationIsNotOverwritten() {
        Patient asSeen = imported(hospitalA, freshPhone(), "MRN-" + uniq());
        // A human edits the address after the preview.
        jdbc.update("UPDATE patients SET address = 'Reception Corrected' WHERE id = ?", asSeen.getId());

        ImportWriteResult r = persister.update(update(asSeen, Map.of("email", "y@example.test", "address", "File Addr"), false, false), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION);
        Patient after = reload(asSeen.getId());
        assertThat(after.getAddress()).isEqualTo("Reception Corrected");
        assertThat(after.getEmail()).isNull();
        assertThat(row("patient_import_links", "patient_id = ?", asSeen.getId()).get("last_batch_id")).isEqualTo(batchA);
    }

    @Test
    void anUpdateLosingThePhoneRaceLeavesTheTargetEntirelyUnchangedIncludingItsAcknowledgement() {
        String oldPhone = freshPhone();
        String newPhone = freshPhone();
        Patient target = imported(hospitalA, oldPhone, "MRN-" + uniq());
        // Staff had acknowledged the OLD number (set directly: the importer must never write these).
        jdbc.update("UPDATE patients SET duplicate_phone_ack_for = ?, duplicate_phone_ack_by = 'staff@example.test', duplicate_phone_ack_at = NOW(6) WHERE id = ?", oldPhone, target.getId());
        Patient asSeen = reload(target.getId());
        Map<String, Object> patientBefore = row("patients", "id = ?", target.getId());
        Map<String, Object> linkBefore = row("patient_import_links", "patient_id = ?", target.getId());
        // Between evaluation and write, someone else takes the NEW number.
        Patient rival = manual(hospitalA, newPhone);

        ImportWriteResult r = persister.update(update(asSeen, Map.of("phone", newPhone, "address", "Also Proposed"), false, true), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
        assertThat(r.relatedPatientIds()).containsExactly(rival.getId());
        assertThat(row("patients", "id = ?", target.getId())).isEqualTo(patientBefore); // byte-for-byte: phone, address, ack columns
        assertThat(row("patient_import_links", "patient_id = ?", target.getId())).isEqualTo(linkBefore);
    }

    @Test
    void staleAcknowledgementIsClearedExactlyWhenThePhoneChangeCommits() {
        String oldPhone = freshPhone();
        Patient p = imported(hospitalA, oldPhone, "MRN-" + uniq());
        jdbc.update("UPDATE patients SET duplicate_phone_ack_for = ?, duplicate_phone_ack_by = 'staff@example.test', duplicate_phone_ack_at = NOW(6) WHERE id = ?", oldPhone, p.getId());
        Patient asSeen = reload(p.getId());

        ImportWriteResult r = persister.update(update(asSeen, Map.of("phone", freshPhone()), false, true), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.UPDATED);
        Patient after = reload(p.getId());
        assertThat(after.getDuplicatePhoneAckFor()).isNull();
        assertThat(after.getDuplicatePhoneAckAt()).isNull();
        assertThat(after.getDuplicatePhoneAckBy()).isNull();
        // And nothing in this class ever created one.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE hospital_id = ? AND duplicate_phone_ack_by IS NOT NULL AND duplicate_phone_ack_by <> 'staff@example.test'", Long.class, hospitalA)).isZero();
    }

    @Test
    void aPatientDeactivatedByAnUndoneImportIsReactivatedButAStaffDeactivatedOneIsNot() {
        // Eligible: created by the UNDONE batch, then deactivated (as undo does).
        ImportWriteResult created = persister.create(create(hospitalA, freshPhone(), "MRN-" + uniq()), ctx(hospitalA, undoneBatchA));
        jdbc.update("UPDATE patients SET is_active = 0 WHERE id = ?", created.patientId());
        Patient eligible = reload(created.patientId());

        ImportWriteResult r = persister.update(update(eligible, Map.of(), true, false), ctx(hospitalA, batchA));
        assertThat(r.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(reload(eligible.getId()).getIsActive()).isTrue();

        // Ineligible: created by a live batch, deactivated by staff. The evaluator would never
        // propose this; the writer refuses even if handed such a proposal.
        Patient staffDeactivated = imported(hospitalA, freshPhone(), "MRN-" + uniq());
        jdbc.update("UPDATE patients SET is_active = 0 WHERE id = ?", staffDeactivated.getId());
        Patient asSeen = reload(staffDeactivated.getId());

        ImportWriteResult refused = persister.update(update(asSeen, Map.of(), true, false), ctx(hospitalA, batchA));
        assertThat(refused.reasonCode()).isEqualTo(ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION);
        assertThat(reload(staffDeactivated.getId()).getIsActive()).isFalse();
    }

    @Test
    void anotherHospitalsPatientCannotBeUpdatedAndItsLinkCannotBeUsed() {
        Patient theirs = imported(hospitalB, freshPhone(), "MRN-" + uniq());
        Map<String, Object> before = row("patients", "id = ?", theirs.getId());

        // A proposal that claims hospital A but names B's patient id: the tenant-scoped reload finds nothing.
        UpdateCandidate forged = new UpdateCandidate(
                hospitalA, theirs.getId(), PatientFieldValues.of(theirs), true, null, Map.of("address", "Stolen"), false, false, null, Map.of(), true);
        ImportWriteResult r = persister.update(forged, ctx(hospitalA, batchA));

        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION);
        assertThat(row("patients", "id = ?", theirs.getId())).isEqualTo(before);
        assertThat(row("patient_import_links", "patient_id = ?", theirs.getId()).get("last_batch_id")).isEqualTo(batchB);

        // And a proposal honestly marked as B's cannot be written under A's context at all.
        assertThatThrownBy(() -> persister.update(update(theirs, Map.of("address", "Stolen"), false, false), ctx(hospitalA, batchA)))
                .isInstanceOf(ImportTenantMismatchException.class);
    }

    @Test
    void aNonPhoneDatabaseProblemIsNeverReportedAsAPhoneRace() {
        Patient p = imported(hospitalA, freshPhone(), "MRN-" + uniq());
        // A gender the column cannot hold (length 10) is a deterministic DB constraint, not a race.
        ImportWriteResult r = persister.update(update(p, Map.of("gender", "MALE-BUT-FAR-TOO-LONG"), false, false), ctx(hospitalA, batchA));

        assertThat(r.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(r.reasonCode()).isIn(ImportReasonCode.VALIDATION_FAILED, ImportReasonCode.CONSTRAINT_FAILED);
        assertThat(r.reasonCode()).isNotEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
        assertThat(reload(p.getId()).getGender()).isEqualTo("FEMALE");
    }
}
