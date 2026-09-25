package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.ImportRowResultRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.hospital.PatientDuplicateFinder;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The evaluator against a real (H2, MySQL-mode) schema with the real repositories and the real
 * {@link PatientDuplicateFinder}: the read paths behave as on the database, tenant scoping is
 * exercised by real queries, and — the point of the class — Hibernate's own statistics prove that
 * a whole preview's worth of evaluations performed zero entity inserts, updates and deletes, and
 * that a flush afterwards changes nothing.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({PatientImporter.class, PatientDuplicateFinder.class})
class PatientImporterDryRunSliceTest {

    @Autowired PatientImporter importer;
    @Autowired PatientRepository patients;
    @Autowired PatientImportLinkRepository links;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportRowResultRepository results;
    @Autowired EntityManager em;

    private static final long H1 = 101L;
    private static final long H2 = 102L;

    private static final List<String> HEADERS = List.of("MRN", "Name", "Phone", "Gender", "DOB", "Address", "hospital_id");
    private static final Map<String, String> MAPPING = Map.of(
            "MRN", "legacyId", "Name", "name", "Phone", "phone", "Gender", "gender", "DOB", "dateOfBirth", "Address", "address");
    private final SheetHeader header = new SheetHeader("csv", HEADERS);

    private Patient h1Linked;
    private Patient h1Holder;
    private Patient h1Manual;
    private Patient h2SamePhone;
    private ImportBatch undoneBatch;
    private Patient h1Undone;

    @BeforeEach
    void seed() {
        h1Linked = patient(H1, "Test Linked", "9000000001", LocalDate.of(1980, 1, 1), true);
        h1Manual = patient(H1, "Test Manual", "9000000002", null, true);
        h1Holder = patient(H1, "Test Holder", "9000000006", null, true);
        h2SamePhone = patient(H2, "Other Tenant", "9000000001", null, true); // same phone, other hospital
        patient(H1, "Test Inactive", "9000000003", null, false); // inactive holder of 9000000003

        ImportBatch done = batch(H1, ImportStatus.COMPLETED);
        undoneBatch = batch(H1, ImportStatus.UNDONE);
        h1Undone = patient(H1, "Test Undone", "9000000004", null, false);

        link(h1Linked, "MRN-1", done, PatientFieldValues.of(h1Linked));
        link(h1Undone, "MRN-U", undoneBatch, PatientFieldValues.of(h1Undone));
        // Another tenant using the same MRN string must never be visible to H1.
        Patient h2Mrn = patient(H2, "Other Mrn", "9000000009", null, true);
        link(h2Mrn, "MRN-1", batch(H2, ImportStatus.COMPLETED), PatientFieldValues.of(h2Mrn));

        em.flush();
        em.clear();
    }

    private Patient patient(long hospitalId, String name, String phone, LocalDate dob, boolean active) {
        Patient p = new Patient();
        p.setHospitalId(hospitalId);
        p.setName(name);
        p.setPhone(phone);
        p.setGender("MALE");
        p.setDateOfBirth(dob);
        p.setAddress("Seed Address");
        p.setIsActive(active);
        return patients.saveAndFlush(p);
    }

    private ImportBatch batch(long hospitalId, ImportStatus status) {
        ImportBatch b = new ImportBatch();
        b.setHospitalId(hospitalId);
        b.setFileSha256("0".repeat(64));
        b.setStatus(status);
        return batches.saveAndFlush(b);
    }

    private void link(Patient p, String legacyId, ImportBatch createdBy, PatientFieldValues snapshot) {
        PatientImportLink l = new PatientImportLink();
        l.setPatientId(p.getId());
        l.setHospitalId(p.getHospitalId());
        l.setLegacyId(legacyId);
        l.setCreatedByBatchId(createdBy.getId());
        l.setLastBatchId(createdBy.getId());
        l.setLastImportedAt(LocalDateTime.now());
        l.setLastImportedValuesJson(snapshot.toJson());
        links.saveAndFlush(l);
    }

    private static ParsedRow row(int n, String... v) {
        List<String> vals = new java.util.ArrayList<>(List.of(v));
        while (vals.size() < HEADERS.size()) vals.add("");
        return new ParsedRow(n, vals, List.of());
    }

    private Statistics stats() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void aFullPreviewPerformsZeroInsertsUpdatesOrDeletes() {
        Statistics s = stats();
        s.clear();
        long patientsBefore = patients.count();
        long linksBefore = links.count();
        long batchesBefore = batches.count();
        long resultsBefore = results.count();
        ImportEvaluationContext ctx = new ImportEvaluationContext(H1);

        List<RowEvaluation> out = List.of(
                importer.evaluate(row(2, "MRN-1", "Test Linked", "9000000001", "M", "1980-01-01", "Newer Address"), header, MAPPING, ctx), // update
                importer.evaluate(row(3, "MRN-NEW", "Brand New", "9000000005", "F", "1990-01-01", ""), header, MAPPING, ctx), // create
                importer.evaluate(row(4, "MRN-X", "Someone", "9000000006", "M", "1990-01-01", ""), header, MAPPING, ctx), // existing holder → review
                importer.evaluate(row(5, "MRN-U", "Test Undone", "9000000004", "M", "", ""), header, MAPPING, ctx), // reactivate
                importer.evaluate(row(6, "MRN-Y", "Reuse", "9000000003", "M", "1990-01-01", ""), header, MAPPING, ctx), // inactive holder → free
                importer.evaluate(row(7, "", "Test Manual", "9000000002", "M", "", "Seed Address"), header, mappingWithoutMrn(), ctx), // no change
                importer.evaluate(row(8, "MRN-Z", "Third", "9000000006", "M", "1990-01-01", ""), header, MAPPING, ctx)); // in-file dup of row 4

        assertThat(out).extracting(RowEvaluation::state)
                .containsExactly(
                        ImportRowState.UPDATED,
                        ImportRowState.CREATED,
                        ImportRowState.NEEDS_REVIEW,
                        ImportRowState.UPDATED,
                        ImportRowState.CREATED,
                        ImportRowState.SKIPPED,
                        ImportRowState.NEEDS_REVIEW);
        assertThat(out.get(2).reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW);
        assertThat(out.get(2).relatedPatientIds()).containsExactly(h1Holder.getId());
        assertThat(out.get(3).update().reactivate()).isTrue();
        assertThat(out.get(6).reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_IN_FILE);

        em.flush(); // any mutation of a managed entity would become an UPDATE here
        assertThat(s.getEntityInsertCount()).isZero();
        assertThat(s.getEntityUpdateCount()).isZero();
        assertThat(s.getEntityDeleteCount()).isZero();
        assertThat(patients.count()).isEqualTo(patientsBefore);
        assertThat(links.count()).isEqualTo(linksBefore);
        assertThat(batches.count()).isEqualTo(batchesBefore);
        assertThat(results.count()).isEqualTo(resultsBefore);

        em.clear();
        Patient reloaded = patients.findById(h1Linked.getId()).orElseThrow();
        assertThat(reloaded.getAddress()).isEqualTo("Seed Address");
        assertThat(patients.findById(h1Undone.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    void mrnLookupsAreTenantScopedByTheRealQuery() {
        // H1 owns MRN-1 → its own linked patient; H2 owns a different MRN-1. Evaluating as H2 must see H2's.
        RowEvaluation asH1 = importer.evaluate(row(2, "MRN-1", "Test Linked", "9000000001", "M", "", "Seed Address"), header, MAPPING, new ImportEvaluationContext(H1));
        RowEvaluation asH2 = importer.evaluate(row(2, "MRN-1", "Other Mrn", "9000000009", "M", "", "Seed Address"), header, MAPPING, new ImportEvaluationContext(H2));

        assertThat(asH1.matchedPatientId()).isEqualTo(h1Linked.getId());
        assertThat(asH2.matchedPatientId()).isNotEqualTo(h1Linked.getId());
        assertThat(asH2.state()).isEqualTo(ImportRowState.SKIPPED); // identical to H2's own record
    }

    @Test
    void phoneLookupsAreTenantScopedAndIgnoreInactiveHolders() {
        ImportEvaluationContext h2 = new ImportEvaluationContext(H2);
        // 9000000002 is H1's manual patient; for H2 the number is free.
        RowEvaluation free = importer.evaluate(row(2, "MRN-Q", "Fresh", "9000000002", "M", "1990-01-01"), header, MAPPING, h2);
        assertThat(free.state()).isEqualTo(ImportRowState.CREATED);

        // 9000000001 is held by H2's own patient → review, and the related id is H2's, not H1's.
        RowEvaluation held = importer.evaluate(row(3, "MRN-R", "Fresh Two", "9000000001", "M", "1990-01-01"), header, MAPPING, h2);
        assertThat(held.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW);
        assertThat(held.relatedPatientIds()).containsExactly(h2SamePhone.getId());

        // In H1, 9000000003 is held only by an inactive patient → free.
        RowEvaluation inactive = importer.evaluate(row(4, "MRN-S", "Fresh Three", "9000000003", "M", "1990-01-01"), header, MAPPING, new ImportEvaluationContext(H1));
        assertThat(inactive.state()).isEqualTo(ImportRowState.CREATED);
    }

    @Test
    void theFilesHospitalIdColumnIsDataNotTenancy() {
        RowEvaluation e = importer.evaluate(
                row(2, "MRN-NEW", "Brand New", "9000000005", "F", "1990-01-01", "", String.valueOf(H2)), header, MAPPING, new ImportEvaluationContext(H1));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(e.create().customFields()).containsEntry("hospital_id", String.valueOf(H2));
        // The number 9000000001 is held in H1 — evaluating the same row with that phone under H1's context sees H1's holder.
        RowEvaluation e2 = importer.evaluate(
                row(3, "MRN-NEW2", "Brand New", "9000000001", "F", "1990-01-01", "", String.valueOf(H2)), header, MAPPING, new ImportEvaluationContext(H1));
        assertThat(e2.relatedPatientIds()).containsExactly(h1Linked.getId());
    }

    private static Map<String, String> mappingWithoutMrn() {
        Map<String, String> m = new java.util.LinkedHashMap<>(MAPPING);
        m.remove("MRN");
        return m;
    }
}
