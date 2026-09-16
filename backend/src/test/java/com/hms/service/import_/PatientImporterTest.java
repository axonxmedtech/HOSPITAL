package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hms.dto.DuplicatePatientMatch;
import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.hospital.PatientDuplicateFinder;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The evaluator, decision by decision, against mocked read paths. Every test ends by proving the
 * evaluator wrote nothing and touched no managed entity — that is the property a preview rests
 * on. All names, phones and MRNs are synthetic.
 */
class PatientImporterTest {

    private static final long HOSPITAL = 7L;
    private static final long OTHER_HOSPITAL = 8L;

    private final PatientImportLinkRepository links = mock(PatientImportLinkRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final PatientDuplicateFinder finder = mock(PatientDuplicateFinder.class);
    private final PatientImporter importer = new PatientImporter(links, patients, batches, finder);

    private ImportEvaluationContext ctx;

    @BeforeEach
    void freshContext() {
        ctx = new ImportEvaluationContext(HOSPITAL);
        when(patients.findActiveByPhoneOrdered(anyString(), anyLong())).thenReturn(List.of());
        when(finder.findActiveByPhone(anyLong(), anyString(), any())).thenReturn(List.of());
    }

    /** Scenario 52 for every test: nothing was written, deleted or flushed through any repository. */
    @AfterEach
    void zeroWrites() {
        verify(patients, never()).save(any());
        verify(patients, never()).saveAll(any());
        verify(patients, never()).saveAndFlush(any());
        verify(patients, never()).delete(any());
        verify(patients, never()).deleteById(any());
        verify(links, never()).save(any());
        verify(links, never()).saveAll(any());
        verify(links, never()).saveAndFlush(any());
        verify(links, never()).delete(any());
        verify(batches, never()).save(any());
        verify(batches, never()).saveAll(any());
        verify(batches, never()).delete(any());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static final List<String> HEADERS = List.of("MRN", "Name", "Phone", "Gender", "DOB", "Email", "Address", "History", "hospital_id", "Caste");
    private static final Map<String, String> MAPPING = Map.of(
            "MRN", "legacyId",
            "Name", "name",
            "Phone", "phone",
            "Gender", "gender",
            "DOB", "dateOfBirth",
            "Email", "email",
            "Address", "address",
            "History", "medicalHistory");

    private static SheetHeader header() {
        return new SheetHeader("csv", HEADERS);
    }

    private static Map<String, String> mappingWithoutMrn() {
        Map<String, String> m = new LinkedHashMap<>(MAPPING);
        m.remove("MRN");
        return m;
    }

    private static ParsedRow row(int rowNum, String... valuesInHeaderOrder) {
        List<String> v = new java.util.ArrayList<>(List.of(valuesInHeaderOrder));
        while (v.size() < HEADERS.size()) v.add("");
        return new ParsedRow(rowNum, v, List.of());
    }

    private static Patient patient(long id, String name, String phone, LocalDate dob, boolean active) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(HOSPITAL);
        p.setCustomId("PAT" + id);
        p.setName(name);
        p.setPhone(phone);
        p.setGender("MALE");
        p.setDateOfBirth(dob);
        p.setAddress("Old Address");
        p.setIsActive(active);
        return p;
    }

    private static PatientImportLink link(long patientId, String legacyId, Long createdBy, PatientFieldValues snapshot) {
        PatientImportLink l = new PatientImportLink();
        l.setPatientId(patientId);
        l.setHospitalId(HOSPITAL);
        l.setLegacyId(legacyId);
        l.setCreatedByBatchId(createdBy);
        l.setLastBatchId(createdBy == null ? 1L : createdBy);
        l.setLastImportedValuesJson(snapshot == null ? null : snapshot.toJson());
        return l;
    }

    private RowEvaluation evaluate(ParsedRow row) {
        return importer.evaluate(row, header(), MAPPING, ctx);
    }

    private RowEvaluation evaluateNoMrn(ParsedRow row) {
        return importer.evaluate(row, header(), mappingWithoutMrn(), ctx);
    }

    private static String snapshotOf(Patient p) {
        return PatientFieldValues.of(p).toJson();
    }

    // ── parser problems take precedence ─────────────────────────────────────

    @Test
    void aDroppedOverLongCellFailsTheRowWithNoCandidate() {
        ParsedRow r = new ParsedRow(
                5,
                List.of("MRN1", "Test Person", "9000000001", "M", "", "", "", "", "", ""),
                List.of(new RowProblem(RowProblem.Code.CELL_TOO_LONG, "Address", 6)));

        RowEvaluation e = evaluate(r);

        assertThat(e.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.VALUE_TOO_LONG);
        assertThat(e.column()).isEqualTo("Address");
        assertThat(e.create()).isNull();
        assertThat(e.update()).isNull();
        verify(links, never()).findByHospitalIdAndLegacyId(anyLong(), anyString()); // never even looked
    }

    @Test
    void aStrayValueBeyondTheHeaderFailsTheRow() {
        ParsedRow r = new ParsedRow(
                6, List.of("MRN1", "Test Person", "9000000001", "M", "", "", "", "", "", ""),
                List.of(new RowProblem(RowProblem.Code.UNEXPECTED_CELL, "K", 10)));

        RowEvaluation e = evaluate(r);

        assertThat(e.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.UNEXPECTED_COLUMN);
        assertThat(e.create()).isNull();
    }

    // ── name, email, lengths ────────────────────────────────────────────────

    @Test
    void blankNameFails() {
        RowEvaluation e = evaluate(row(2, "MRN1", "   ", "9000000001", "M"));
        assertThat(e.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.NAME_MISSING);
        assertThat(e.column()).isEqualTo("Name");
    }

    @Test
    void anEntityInvalidNameFailsRatherThanBeingCleaned() {
        assertThat(evaluate(row(2, "MRN1", "Test 😀 Person", "9000000001", "M")).reasonCode()).isEqualTo(ImportReasonCode.VALIDATION_FAILED);
        assertThat(evaluate(row(3, "MRN2", "x".repeat(101), "9000000001", "M")).reasonCode()).isEqualTo(ImportReasonCode.VALIDATION_FAILED);
        assertThat(evaluate(row(4, "MRN3", "x".repeat(100), "9000000001", "M", "01/01/1990")).state()).isEqualTo(ImportRowState.CREATED);
    }

    @Test
    void anInvalidEmailIsPreservedAsImportMetadataAndPatientEmailStaysEmpty() {
        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "01/01/1990", "n/a"));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(e.create().values().email()).isNull();
        assertThat(e.create().customFields()).containsEntry(PatientImporter.EMAIL_NOT_VALID, "n/a");

        RowEvaluation ok = evaluate(row(3, "MRN2", "Test Person", "9000000002", "M", "01/01/1990", "person@example.test"));
        assertThat(ok.create().values().email()).isEqualTo("person@example.test");
        assertThat(ok.create().customFields()).doesNotContainKey(PatientImporter.EMAIL_NOT_VALID);
    }

    @Test
    void addressAndHistoryOverTheBusinessLimitAreClampedWithTheFullValuePreservedButIdentityFieldsNeverAre() {
        String longAddress = "a".repeat(300);
        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "01/01/1990", "", longAddress, "h".repeat(1001)));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(e.create().values().address()).hasSize(255);
        assertThat(e.create().customFields()).containsEntry(PatientImporter.ADDRESS_FULL, longAddress);
        assertThat(e.create().values().medicalHistory()).hasSize(1000);
        assertThat(e.create().customFields()).containsKey(PatientImporter.MEDICAL_HISTORY_FULL);

        RowEvaluation mrn = evaluate(row(3, "m".repeat(101), "Test Person", "9000000002", "M"));
        assertThat(mrn.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(mrn.reasonCode()).isEqualTo(ImportReasonCode.VALIDATION_FAILED);
    }

    @Test
    void invalidDobFailsAndGenderIsNotGuessedForANewPatient() {
        assertThat(evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "31/02/2020")).reasonCode()).isEqualTo(ImportReasonCode.INVALID_DOB);
        RowEvaluation g = evaluate(row(3, "MRN2", "Test Person", "9000000002", "Unknown"));
        assertThat(g.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(g.reasonCode()).isEqualTo(ImportReasonCode.INVALID_GENDER);
        assertThat(evaluate(row(4, "MRN3", "Test Person", "9000000003", "")).reasonCode()).isEqualTo(ImportReasonCode.INVALID_GENDER);
    }

    // ── create path ─────────────────────────────────────────────────────────

    @Test
    void aCleanRowBecomesACreateCandidateWithNormalisedValuesAndPreservedExtras() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.empty());

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "+91 98765-43210", "female", "03/04/1977", "", "12 Road", "", "999", "X"));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        CreateCandidate c = e.create();
        assertThat(c.legacyId()).isEqualTo("MRN1");
        assertThat(c.values().name()).isEqualTo("Test Person");
        assertThat(c.values().phone()).isEqualTo("9876543210");
        assertThat(c.values().gender()).isEqualTo("FEMALE");
        assertThat(c.values().dateOfBirth()).isEqualTo(LocalDate.of(1977, 4, 3));
        assertThat(c.customFields())
                .containsEntry(PatientImporter.PHONE_AS_IMPORTED, "+91 98765-43210")
                .containsEntry("hospital_id", "999") // ordinary data, never tenancy
                .containsEntry("Caste", "X");
        verify(finder).findActiveByPhone(HOSPITAL, "9876543210", null);
    }

    @Test
    void anUnknownMrnCreatesAndNeverFallsBackToNamePlusPhone() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "NEW-1")).thenReturn(Optional.empty());
        // A patient with the same name and phone exists but has no MRN link: the MRN path must not touch it.
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL))
                .thenReturn(List.of(patient(50L, "Test Person", "9000000001", null, true)));
        when(finder.findActiveByPhone(HOSPITAL, "9000000001", null))
                .thenReturn(List.of(new DuplicatePatientMatch(50L, "p50", "PAT50", "Test Person", 40)));

        RowEvaluation e = evaluate(row(2, "NEW-1", "Test Person", "9000000001", "M", "01/01/1990"));

        // It is a create — which then trips the real duplicate-phone rule, as it must.
        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW);
        assertThat(e.update()).isNull();
        verify(patients, never()).findActiveByPhoneOrdered(anyString(), anyLong());
    }

    // ── MRN matching ────────────────────────────────────────────────────────

    @Test
    void anMrnLinkToAnActivePatientBecomesAnUpdateOfOnlyTheChangedFields() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "03/04/1977", "", "New Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().patientId()).isEqualTo(10L);
        assertThat(e.update().changes()).containsExactly(Map.entry("address", "New Address"));
        assertThat(e.update().reactivate()).isFalse();
        assertThat(e.update().clearStalePhoneAcknowledgement()).isFalse();
        assertThat(existing.getAddress()).isEqualTo("Old Address"); // the managed entity is untouched
        verify(finder).findActiveByPhone(HOSPITAL, "9000000001", 10L); // excludes itself
    }

    @Test
    void anMrnRowIdenticalToTheCurrentRecordIsSkippedAsNoChange() {
        Patient existing = patient(10L, "Test Person", "9000000001", null, true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "", "", "Old Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.SKIPPED);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.NO_CHANGE);
    }

    @Test
    void anInactiveLinkedPatientNotUndoneByAnImportIsReview() {
        Patient inactive = patient(10L, "Test Person", "9000000001", null, false);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 3L, snapshot(inactive))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(inactive));
        ImportBatch completed = new ImportBatch();
        completed.setStatus(ImportStatus.COMPLETED);
        when(batches.findByIdAndHospitalId(3L, HOSPITAL)).thenReturn(Optional.of(completed));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.INACTIVE_MATCH);
        assertThat(e.matchedPatientId()).isEqualTo(10L);
    }

    @Test
    void aPatientDeactivatedByAnUndoneImportIsAReactivationCandidate() {
        Patient inactive = patient(10L, "Test Person", "9000000001", null, false);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 3L, snapshot(inactive))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(inactive));
        ImportBatch undone = new ImportBatch();
        undone.setStatus(ImportStatus.UNDONE);
        when(batches.findByIdAndHospitalId(3L, HOSPITAL)).thenReturn(Optional.of(undone));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().reactivate()).isTrue();
        assertThat(e.update().changes()).isEmpty();
        assertThat(inactive.getIsActive()).isFalse(); // not flipped here
    }

    @Test
    void anMrnWhoseNameAndDateOfBirthBothDifferIsAnIdentityMismatch() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation both = evaluate(row(2, "MRN1", "Completely Different", "9000000001", "M", "01/01/1990"));
        assertThat(both.reasonCode()).isEqualTo(ImportReasonCode.MRN_IDENTITY_MISMATCH);

        // Name alone differing is a legitimate correction from the source system (subject to ownership).
        RowEvaluation nameOnly = evaluate(row(3, "MRN1", "Test Person Corrected", "9000000001", "M", "03/04/1977"));
        assertThat(nameOnly.reasonCode()).isNotEqualTo(ImportReasonCode.MRN_IDENTITY_MISMATCH);
    }

    // ── no-MRN matching ─────────────────────────────────────────────────────

    @Test
    void withoutAnMrnAnExactNameAndCanonicalPhoneMatchIsAnUpdate() {
        Patient existing = patient(20L, "Ramesh Kumar", "9000000001", null, true);
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL)).thenReturn(List.of(existing));
        when(links.findByHospitalIdAndPatientId(HOSPITAL, 20L)).thenReturn(Optional.of(link(20L, null, 1L, PatientFieldValues.of(existing))));

        RowEvaluation e = evaluateNoMrn(row(2, "", "ramesh-kumar", "+91 90000 00001", "M", "", "", "Newer Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().patientId()).isEqualTo(20L);
        assertThat(e.update().changes()).containsKey("address");
        assertThat(e.update().changes()).doesNotContainKey("name"); // "ramesh-kumar" ≠ stored name: file value differs
    }

    @Test
    void withoutAnMrnAnyDateOfBirthDisagreementIsAmbiguous() {
        Patient existing = patient(20L, "Ramesh Kumar", "9000000001", LocalDate.of(1980, 1, 1), true);
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL)).thenReturn(List.of(existing));

        RowEvaluation e = evaluateNoMrn(row(2, "", "Ramesh Kumar", "9000000001", "M", "02/02/1982"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.AMBIGUOUS_PATIENT_MATCH);
        assertThat(e.matchedPatientId()).isEqualTo(20L);
    }

    @Test
    void withoutAnMrnMoreThanOneExactMatchIsAmbiguous() {
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL))
                .thenReturn(List.of(patient(20L, "Ramesh Kumar", "9000000001", null, true), patient(21L, "RAMESH KUMAR", "9000000001", null, true)));

        RowEvaluation e = evaluateNoMrn(row(2, "", "Ramesh Kumar", "9000000001", "M"));

        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.AMBIGUOUS_PATIENT_MATCH);
        assertThat(e.relatedPatientIds()).containsExactly(20L, 21L);
    }

    @Test
    void aSpellingVariantIsNotAMatchItIsADuplicatePhoneForReview() {
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL)).thenReturn(List.of(patient(20L, "Ramesh Kumar", "9000000001", null, true)));
        when(finder.findActiveByPhone(HOSPITAL, "9000000001", null))
                .thenReturn(List.of(new DuplicatePatientMatch(20L, "p20", "PAT20", "Ramesh Kumar", 40)));

        RowEvaluation e = evaluateNoMrn(row(2, "", "Ramesh Kumaar", "9000000001", "M", "01/01/1990"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW);
        assertThat(e.update()).isNull();
    }

    @Test
    void withoutAnMrnABlankOrUnreadablePhoneCannotMatchOrRegister() {
        RowEvaluation blank = evaluateNoMrn(row(2, "", "Test Person", "", "M"));
        assertThat(blank.reasonCode()).isEqualTo(ImportReasonCode.PHONE_MISSING);
        RowEvaluation bad = evaluateNoMrn(row(3, "", "Test Person", "N/A", "M"));
        assertThat(bad.reasonCode()).isEqualTo(ImportReasonCode.PHONE_UNRECOVERABLE);
        assertThat(bad.message()).doesNotContain("N/A"); // the raw value is not echoed
        verify(patients, never()).findActiveByPhoneOrdered(anyString(), anyLong());
    }

    @Test
    void anMrnUpdateWithABlankPhoneLeavesTheExistingPhoneAloneWhileACreateNeedsOne() {
        Patient existing = patient(10L, "Test Person", "9000000001", null, true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation upd = evaluate(row(2, "MRN1", "Test Person", "", "M", "", "", "Newer Address"));
        assertThat(upd.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(upd.update().changes()).doesNotContainKey("phone");

        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN9")).thenReturn(Optional.empty());
        RowEvaluation cre = evaluate(row(3, "MRN9", "Test Person", "", "M"));
        assertThat(cre.reasonCode()).isEqualTo(ImportReasonCode.PHONE_MISSING);
    }

    // ── duplicate phones ────────────────────────────────────────────────────

    @Test
    void theSameCanonicalPhoneUnderTwoIdentitiesInOneFileIsReview() {
        when(links.findByHospitalIdAndLegacyId(anyLong(), anyString())).thenReturn(Optional.empty());

        RowEvaluation first = evaluate(row(2, "MRN1", "Person One", "9000000001", "M", "01/01/1990"));
        RowEvaluation same = evaluate(row(3, "MRN1", "Person One", "98765 43210", "M")); // same MRN again → skipped, not a phone conflict
        RowEvaluation second = evaluate(row(4, "MRN2", "Person Two", "+91 9000000001", "F"));
        RowEvaluation noMrn = evaluateNoMrn(row(5, "", "Person Three", "09000000001", "F"));

        assertThat(first.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(same.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_MRN_IN_FILE);
        assertThat(second.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_IN_FILE);
        assertThat(second.message()).contains("row 2");
        assertThat(second.phoneMasked()).isEqualTo("90******01");
        assertThat(noMrn.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_IN_FILE);
    }

    @Test
    void anExistingActiveHolderIsReviewAndAnExistingAcknowledgementDoesNotAuthoriseAThirdPerson() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.empty());
        // The finder is staging's: it already excludes inactive rows and other hospitals, and it
        // still lists an ACKNOWLEDGED sharer as a holder — that acknowledgement was between two
        // specific people and says nothing about a third.
        when(finder.findActiveByPhone(HOSPITAL, "9000000001", null))
                .thenReturn(List.of(new DuplicatePatientMatch(30L, "p30", "PAT30", "Sharer", 50)));

        RowEvaluation e = evaluate(row(2, "MRN1", "Third Person", "9000000001", "M", "01/01/1990"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW);
        assertThat(e.relatedPatientIds()).containsExactly(30L);
        assertThat(e.message()).contains("PAT30").doesNotContain("9000000001");
        assertThat(e.create()).isNull();
    }

    @Test
    void theFinderIsAskedWithTheCallersHospitalOnlyAndAnEmptyAnswerMeansNoConflict() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.empty());

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "01/01/1990"));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        verify(finder).findActiveByPhone(HOSPITAL, "9000000001", null);
        verify(finder, never()).findActiveByPhone(eq(OTHER_HOSPITAL), anyString(), any());
    }

    @Test
    void anUpdateChangingThePhoneDetectsAStaleAcknowledgementWithoutClearingIt() {
        Patient existing = patient(10L, "Test Person", "9000000001", null, true);
        existing.setDuplicatePhoneAckFor("9000000001");
        existing.setDuplicatePhoneAckBy("staff@example.test");
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000002", "M"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().changes()).containsEntry("phone", "9000000002");
        assertThat(e.update().clearStalePhoneAcknowledgement()).isTrue();
        assertThat(existing.getDuplicatePhoneAckFor()).isEqualTo("9000000001"); // untouched
        assertThat(existing.getDuplicatePhoneAckBy()).isEqualTo("staff@example.test");
        assertThat(existing.getPhone()).isEqualTo("9000000001");
        verify(finder).findActiveByPhone(HOSPITAL, "9000000002", 10L);
    }

    // ── field ownership ─────────────────────────────────────────────────────

    @Test
    void aFieldAHumanChangedSinceTheLastImportBlocksTheWholeRow() {
        Patient existing = patient(10L, "Test Person", "9000000001", null, true);
        PatientFieldValues lastImport = new PatientFieldValues("Test Person", "9000000001", "MALE", null, null, "Imported Address", null);
        existing.setAddress("Staff Corrected Address"); // human-owned now
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, lastImport)));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        // The file wants a different address AND a new email; the email alone would be fine.
        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "", "new@example.test", "Yet Another Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.EDITED_SINCE_IMPORT);
        assertThat(e.column()).isEqualTo("Address");
        assertThat(e.update()).isNull(); // no partial application of the email
        assertThat(existing.getEmail()).isNull();
    }

    @Test
    void aHumanChangedFieldWhoseFileValueEqualsTheCurrentValueIsNoConflict() {
        Patient existing = patient(10L, "Test Person", "9000000001", null, true);
        PatientFieldValues lastImport = new PatientFieldValues("Test Person", "9000000001", "MALE", null, null, "Imported Address", null);
        existing.setAddress("Staff Corrected Address");
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, lastImport)));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "", "new@example.test", "Staff Corrected Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().changes()).containsExactly(Map.entry("email", "new@example.test"));
    }

    @Test
    void withNoSnapshotEveryFilledFieldCountsAsHumanOwnedButBlanksMayBeFilled() {
        // A manually registered patient matched by name+phone: no link, no snapshot.
        Patient existing = patient(20L, "Ramesh Kumar", "9000000001", null, true);
        existing.setEmail(null);
        when(patients.findActiveByPhoneOrdered("9000000001", HOSPITAL)).thenReturn(List.of(existing));
        when(links.findByHospitalIdAndPatientId(HOSPITAL, 20L)).thenReturn(Optional.empty());

        RowEvaluation conflict = evaluateNoMrn(row(2, "", "Ramesh Kumar", "9000000001", "M", "", "", "File Address"));
        assertThat(conflict.reasonCode()).isEqualTo(ImportReasonCode.EDITED_SINCE_IMPORT);

        ctx = new ImportEvaluationContext(HOSPITAL); // a second run: the same no-MRN phone twice in ONE file is an in-file duplicate
        RowEvaluation fill = evaluateNoMrn(row(3, "", "Ramesh Kumar", "9000000001", "M", "", "person@example.test"));
        assertThat(fill.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(fill.update().changes()).containsExactly(Map.entry("email", "person@example.test"));
        assertThat(fill.update().hadSnapshot()).isFalse();
    }

    @Test
    void theFileNeverBlanksAField() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "", "", ""));

        assertThat(e.state()).isEqualTo(ImportRowState.SKIPPED);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.NO_CHANGE);
        assertThat(existing.getAddress()).isEqualTo("Old Address");
        assertThat(existing.getDateOfBirth()).isEqualTo(LocalDate.of(1977, 4, 3));
    }

    // ── tenancy ─────────────────────────────────────────────────────────────

    @Test
    void everyLookupCarriesTheContextHospitalAndTheFileCannotChangeIt() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.empty());

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "01/01/1990", "", "", "", String.valueOf(OTHER_HOSPITAL)));

        assertThat(e.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(e.create().customFields()).containsEntry("hospital_id", "8");
        verify(links).findByHospitalIdAndLegacyId(HOSPITAL, "MRN1");
        verify(links, never()).findByHospitalIdAndLegacyId(eq(OTHER_HOSPITAL), anyString());
        verify(finder).findActiveByPhone(HOSPITAL, "9000000001", null);
        verify(patients, never()).findById(anyLong());
    }

    @Test
    void aLinkedPatientIsFetchedThroughTheTenantScopedLookupOnly() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, null)));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.empty()); // e.g. purged, or another tenant's id somehow

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M"));

        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.INACTIVE_MATCH);
        verify(patients).findByIdAndHospitalId(10L, HOSPITAL);
        verify(patients, never()).findById(anyLong());
    }

    @Test
    void theContextRefusesToExistWithoutAHospital() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ImportEvaluationContext(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── correction 1: date of birth ─────────────────────────────────────────

    @Test
    void aNewPatientWithNoDobColumnMappedIsDobMissing() {
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.empty());
        Map<String, String> noDob = new LinkedHashMap<>(MAPPING);
        noDob.remove("DOB");

        RowEvaluation e = importer.evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "01/01/1990"), header(), noDob, ctx);

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.DOB_MISSING);
        assertThat(e.message()).contains("No date of birth column");
        assertThat(e.create()).isNull();
    }

    @Test
    void aNewPatientWithABlankDobIsDobMissingAndWithAValidOneIsCreated() {
        when(links.findByHospitalIdAndLegacyId(anyLong(), anyString())).thenReturn(Optional.empty());

        RowEvaluation blank = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", ""));
        assertThat(blank.reasonCode()).isEqualTo(ImportReasonCode.DOB_MISSING);
        assertThat(blank.column()).isEqualTo("DOB");
        assertThat(blank.create()).isNull();

        RowEvaluation ok = evaluate(row(3, "MRN2", "Test Person", "9000000002", "M", "03/04/1977"));
        assertThat(ok.state()).isEqualTo(ImportRowState.CREATED);
        assertThat(ok.create().values().dateOfBirth()).isEqualTo(LocalDate.of(1977, 4, 3));

        // Unparseable stays FAILED: INVALID_DOB, as before.
        assertThat(evaluate(row(4, "MRN3", "Test Person", "9000000003", "M", "31/02/2020")).reasonCode()).isEqualTo(ImportReasonCode.INVALID_DOB);
    }

    @Test
    void anUpdateWithTheDobUnmappedOrBlankLeavesTheExistingDobAlone() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));
        Map<String, String> noDob = new LinkedHashMap<>(MAPPING);
        noDob.remove("DOB");

        RowEvaluation unmapped = importer.evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "", "", "New Address"), header(), noDob, ctx);
        assertThat(unmapped.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(unmapped.update().changes()).containsOnlyKeys("address");

        ctx = new ImportEvaluationContext(HOSPITAL);
        RowEvaluation blank = evaluate(row(3, "MRN1", "Test Person", "9000000001", "M", "", "", "Newer Address"));
        assertThat(blank.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(blank.update().changes()).containsOnlyKeys("address");
        assertThat(existing.getDateOfBirth()).isEqualTo(LocalDate.of(1977, 4, 3));
    }

    @Test
    void anUpdateWithAChangedDobFollowsTheOwnershipRules() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        PatientFieldValues imported = PatientFieldValues.of(existing); // DOB import-owned
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, imported)));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation owned = evaluate(row(2, "MRN1", "Test Person", "9000000001", "M", "04/04/1977"));
        assertThat(owned.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(owned.update().changes()).containsExactly(Map.entry("dateOfBirth", "1977-04-04"));

        existing.setDateOfBirth(LocalDate.of(1977, 4, 5)); // a human corrected it since
        ctx = new ImportEvaluationContext(HOSPITAL);
        RowEvaluation human = evaluate(row(3, "MRN1", "Test Person", "9000000001", "M", "04/04/1977"));
        assertThat(human.reasonCode()).isEqualTo(ImportReasonCode.EDITED_SINCE_IMPORT);
        assertThat(human.update()).isNull();
    }

    // ── correction 2: phone on update ───────────────────────────────────────

    @Test
    void anUpdateWithThePhoneUnmappedOrBlankLeavesTheExistingPhoneAlone() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));
        Map<String, String> noPhone = new LinkedHashMap<>(MAPPING);
        noPhone.remove("Phone");

        RowEvaluation unmapped = importer.evaluate(row(2, "MRN1", "Test Person", "N/A", "M", "", "", "New Address"), header(), noPhone, ctx);
        assertThat(unmapped.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(unmapped.update().changes()).containsOnlyKeys("address");

        ctx = new ImportEvaluationContext(HOSPITAL);
        RowEvaluation blank = evaluate(row(3, "MRN1", "Test Person", "", "M", "", "", "Newer Address"));
        assertThat(blank.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(blank.update().changes()).containsOnlyKeys("address");
        assertThat(existing.getPhone()).isEqualTo("9000000001");
        verify(finder, never()).findActiveByPhone(anyLong(), anyString(), any());
    }

    @Test
    void anUpdateWithAMalformedSuppliedPhoneIsReviewedAndAppliesNothingElse() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "98765ABC10", "M", "", "new@example.test", "New Address"));

        assertThat(e.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(e.reasonCode()).isEqualTo(ImportReasonCode.PHONE_UNRECOVERABLE);
        assertThat(e.matchedPatientId()).isEqualTo(10L);
        assertThat(e.update()).isNull(); // no candidate, so no partial email/address change
        assertThat(e.message()).doesNotContain("98765ABC10");
        assertThat(existing.getEmail()).isNull();
        assertThat(existing.getAddress()).isEqualTo("Old Address");
        verify(finder, never()).findActiveByPhone(anyLong(), anyString(), any());
    }

    @Test
    void anUpdateWithAFormattedButValidPhoneGoesThroughNormalProcessing() {
        Patient existing = patient(10L, "Test Person", "9000000001", LocalDate.of(1977, 4, 3), true);
        when(links.findByHospitalIdAndLegacyId(HOSPITAL, "MRN1")).thenReturn(Optional.of(link(10L, "MRN1", 1L, PatientFieldValues.of(existing))));
        when(patients.findByIdAndHospitalId(10L, HOSPITAL)).thenReturn(Optional.of(existing));

        RowEvaluation e = evaluate(row(2, "MRN1", "Test Person", "+91 90000-00002", "M"));

        assertThat(e.state()).isEqualTo(ImportRowState.UPDATED);
        assertThat(e.update().changes()).containsExactly(Map.entry("phone", "9000000002"));
        assertThat(e.update().customFields()).containsEntry(PatientImporter.PHONE_AS_IMPORTED, "+91 90000-00002");
        verify(finder).findActiveByPhone(HOSPITAL, "9000000002", 10L);
    }

    private static PatientFieldValues snapshot(Patient p) {
        return PatientFieldValues.of(p);
    }
}
