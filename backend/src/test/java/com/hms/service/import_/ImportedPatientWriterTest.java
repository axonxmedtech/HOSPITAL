package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.hospital.DuplicatePhoneAcknowledgement;
import com.hms.service.hospital.PatientRegistrar;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The writer's rules against mocked repositories: it registers through PatientRegistrar, refuses
 * a foreign tenant, applies only allowlisted proposed fields, keeps createdByBatchId, never sets
 * protected identifiers, and writes a snapshot of only the seven demographic fields. Transaction
 * behaviour (atomicity, races) is proven on MySQL in ImportedPatientWriterIT.
 */
class ImportedPatientWriterTest {

    private static final long H = 7L;
    private static final ImportWriteContext CTX = new ImportWriteContext(H, 42L, LocalDateTime.of(2026, 9, 18, 10, 0));

    private final PatientRepository patients = mock(PatientRepository.class);
    private final PatientImportLinkRepository links = mock(PatientImportLinkRepository.class);
    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final PatientRegistrar registrar = mock(PatientRegistrar.class);
    private final ImportedPatientWriter writer = new ImportedPatientWriter(patients, links, batches, registrar, new ObjectMapper());

    private static PatientFieldValues values(String name, String phone, String address) {
        return new PatientFieldValues(name, phone, "MALE", LocalDate.of(1990, 1, 1), null, address, null);
    }

    private static Patient existing(long id, boolean active) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(H);
        p.setPublicId("pub-" + id);
        p.setCustomId("PAT" + id);
        p.setName("Test Person");
        p.setPhone("9000000001");
        p.setGender("MALE");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setAddress("Old Address");
        p.setIsActive(active);
        return p;
    }

    private static UpdateCandidate update(Patient p, Map<String, String> changes, boolean reactivate, boolean clearAck, String legacyId) {
        return new UpdateCandidate(
                H, p.getId(), PatientFieldValues.of(p), Boolean.TRUE.equals(p.getIsActive()), p.getDuplicatePhoneAckFor(),
                changes, reactivate, clearAck, legacyId, Map.of(), true);
    }

    @Test
    void createGoesThroughThePatientRegistrarAndWritesTheLinkWithProvenance() {
        when(registrar.persistNewPatient(any())).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(100L);
            p.setCustomId("PAT100");
            return p;
        });
        when(links.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Long id = writer.create(new CreateCandidate(H, values("Test Person", "9000000001", "Addr"), "MRN-1", Map.of("Caste", "X")), CTX);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(registrar).persistNewPatient(saved.capture());
        Patient p = saved.getValue();
        assertThat(p.getHospitalId()).isEqualTo(H);
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getDuplicatePhoneAckFor()).isNull();
        assertThat(p.getDuplicatePhoneAckAt()).isNull();
        assertThat(p.getDuplicatePhoneAckBy()).isNull();
        verify(patients, never()).save(any());
        verify(patients, never()).saveAll(any());
        verify(patients, never()).saveAndFlush(any());

        ArgumentCaptor<PatientImportLink> link = ArgumentCaptor.forClass(PatientImportLink.class);
        verify(links).saveAndFlush(link.capture());
        assertThat(link.getValue().getPatientId()).isEqualTo(100L);
        assertThat(link.getValue().getHospitalId()).isEqualTo(H);
        assertThat(link.getValue().getLegacyId()).isEqualTo("MRN-1");
        assertThat(link.getValue().getCreatedByBatchId()).isEqualTo(42L);
        assertThat(link.getValue().getLastBatchId()).isEqualTo(42L);
        assertThat(link.getValue().getLastImportedAt()).isEqualTo(CTX.now());
        assertThat(link.getValue().getCustomFieldsJson()).isEqualTo("{\"Caste\":\"X\"}");
        assertThat(PatientFieldValues.fromJson(link.getValue().getLastImportedValuesJson()).phone()).isEqualTo("9000000001");
    }

    @Test
    void aCandidateFromAnotherTenantIsRefusedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> writer.create(new CreateCandidate(8L, values("T", "9000000001", null), null, Map.of()), CTX))
                .isInstanceOf(ImportTenantMismatchException.class);
        Patient p = existing(10L, true);
        assertThatThrownBy(() -> writer.update(
                        new UpdateCandidate(8L, 10L, PatientFieldValues.of(p), true, null, Map.of("address", "x"), false, false, null, Map.of(), true), CTX))
                .isInstanceOf(ImportTenantMismatchException.class);
        verify(registrar, never()).persistNewPatient(any());
        verify(patients, never()).findByIdAndHospitalId(anyLong(), anyLong());
    }

    @Test
    void updateReloadsByIdAndHospitalAppliesOnlyTheProposedFieldsAndKeepsCreatedByBatch() {
        Patient p = existing(10L, true);
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(p));
        PatientImportLink link = new PatientImportLink();
        link.setPatientId(10L);
        link.setHospitalId(H);
        link.setLegacyId("MRN-1");
        link.setCreatedByBatchId(1L);
        link.setLastBatchId(1L);
        link.setLastImportedValuesJson(PatientFieldValues.of(p).toJson());
        when(links.findByHospitalIdAndPatientId(H, 10L)).thenReturn(Optional.of(link));
        when(patients.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(links.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        writer.update(update(p, Map.of("address", "New Address", "email", "p@example.test"), false, false, "MRN-1"), CTX);

        assertThat(p.getAddress()).isEqualTo("New Address");
        assertThat(p.getEmail()).isEqualTo("p@example.test");
        assertThat(p.getName()).isEqualTo("Test Person"); // untouched
        assertThat(p.getPublicId()).isEqualTo("pub-10");
        assertThat(p.getCustomId()).isEqualTo("PAT10");
        assertThat(p.getHospitalId()).isEqualTo(H);
        assertThat(link.getCreatedByBatchId()).isEqualTo(1L); // preserved
        assertThat(link.getLastBatchId()).isEqualTo(42L);
        assertThat(link.getLegacyId()).isEqualTo("MRN-1");
        verify(patients, never()).save(any());
        verify(patients).findByIdAndHospitalId(10L, H);
        verify(patients, never()).findById(anyLong());
    }

    @Test
    void aProposalNamingAProtectedOrUnknownFieldIsRejected() {
        Patient p = existing(10L, true);
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(p));
        when(links.findByHospitalIdAndPatientId(H, 10L)).thenReturn(Optional.empty());

        for (String field : List.of("id", "publicId", "customId", "hospitalId", "isActive", "status", "duplicatePhoneAckFor", "duplicate_phone_ack_by")) {
            assertThatThrownBy(() -> writer.update(update(p, Map.of(field, "x"), false, false, null), CTX))
                    .as(field)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(patients, never()).saveAndFlush(any());
        assertThat(p.getHospitalId()).isEqualTo(H);
    }

    @Test
    void aChangedPatientIsRefusedNotRecomputed() {
        Patient atEvaluation = existing(10L, true);
        Patient now = existing(10L, true);
        now.setAddress("Someone Edited This"); // changed between preview and write
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(now));

        assertThatThrownBy(() -> writer.update(update(atEvaluation, Map.of("email", "p@example.test"), false, false, null), CTX))
                .isInstanceOf(PatientChangedSinceEvaluationException.class)
                .hasMessageNotContaining("Someone Edited This");
        assertThat(now.getEmail()).isNull();
        verify(patients, never()).saveAndFlush(any());
        verify(links, never()).saveAndFlush(any());

        Patient deactivated = existing(10L, false);
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(deactivated));
        assertThatThrownBy(() -> writer.update(update(atEvaluation, Map.of("email", "p@example.test"), false, false, null), CTX))
                .isInstanceOf(PatientChangedSinceEvaluationException.class);

        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> writer.update(update(atEvaluation, Map.of("email", "p@example.test"), false, false, null), CTX))
                .isInstanceOf(PatientChangedSinceEvaluationException.class);
    }

    @Test
    void reactivationIsReVerifiedAgainstTheUndoneBatchNotTrusted() {
        Patient inactive = existing(10L, false);
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(inactive));
        PatientImportLink link = new PatientImportLink();
        link.setPatientId(10L);
        link.setHospitalId(H);
        link.setCreatedByBatchId(3L);
        link.setLastBatchId(3L);
        when(links.findByHospitalIdAndPatientId(H, 10L)).thenReturn(Optional.of(link));
        ImportBatch completed = new ImportBatch();
        completed.setStatus(ImportStatus.COMPLETED); // no longer UNDONE — e.g. it never was
        when(batches.findByIdAndHospitalId(3L, H)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> writer.update(update(inactive, Map.of(), true, false, null), CTX))
                .isInstanceOf(PatientChangedSinceEvaluationException.class);
        assertThat(inactive.getIsActive()).isFalse();
        verify(patients, never()).saveAndFlush(any());
    }

    @Test
    void staleAckIsClearedOnlyWhenTheProposalSaysSoAndThroughTheSharedHelper() {
        Patient p = existing(10L, true);
        p.setDuplicatePhoneAckFor("9000000001");
        p.setDuplicatePhoneAckAt(LocalDateTime.now());
        p.setDuplicatePhoneAckBy("staff@example.test");
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(p));
        when(links.findByHospitalIdAndPatientId(H, 10L)).thenReturn(Optional.empty());
        when(patients.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(links.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        writer.update(update(p, Map.of("phone", "9000000002"), false, true, null), CTX);

        assertThat(p.getPhone()).isEqualTo("9000000002");
        assertThat(p.getDuplicatePhoneAckFor()).isNull();
        assertThat(p.getDuplicatePhoneAckAt()).isNull();
        assertThat(p.getDuplicatePhoneAckBy()).isNull();

        // The helper is exactly what PatientService now calls too.
        Patient q = existing(11L, true);
        q.setDuplicatePhoneAckFor("9000000001");
        assertThat(DuplicatePhoneAcknowledgement.isStaleFor(q, "9000000002")).isTrue();
        assertThat(DuplicatePhoneAcknowledgement.isStaleFor(q, "9000000001")).isFalse();
        DuplicatePhoneAcknowledgement.clear(q);
        assertThat(q.getDuplicatePhoneAckFor()).isNull();
    }

    @Test
    void theSnapshotHoldsOnlyTheSevenDemographicFieldsAndDropsHumanOwnedOnes() {
        Patient p = existing(10L, true);
        p.setAddress("Human Address"); // not what the old snapshot said
        PatientFieldValues old = new PatientFieldValues("Test Person", "9000000001", "MALE", LocalDate.of(1990, 1, 1), null, "Imported Address", null);

        Map<String, String> next = ImportedPatientWriter.nextSnapshot(p, old, Map.of("email", "p@example.test"));

        assertThat(next.keySet()).containsExactlyElementsOf(PatientFieldValues.FIELDS);
        assertThat(next).containsEntry("email", null); // changes map holds the proposal; the entity was not mutated here
        assertThat(next).containsEntry("address", null); // human-owned: not reclaimed
        assertThat(next).containsEntry("name", "Test Person"); // still import-owned
        assertThat(next).doesNotContainKeys("publicId", "customId", "hospitalId", "isActive", "duplicatePhoneAckBy", "duplicatePhoneAckFor");
    }

    @Test
    void anMrnThatDisagreesWithTheLinkIsRefusedRatherThanRewritten() {
        Patient p = existing(10L, true);
        when(patients.findByIdAndHospitalId(10L, H)).thenReturn(Optional.of(p));
        PatientImportLink link = new PatientImportLink();
        link.setPatientId(10L);
        link.setHospitalId(H);
        link.setLegacyId("MRN-1");
        link.setLastBatchId(1L);
        when(links.findByHospitalIdAndPatientId(H, 10L)).thenReturn(Optional.of(link));
        when(patients.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> writer.update(update(p, Map.of("address", "x"), false, false, "MRN-OTHER"), CTX))
                .isInstanceOf(PatientChangedSinceEvaluationException.class);
        assertThat(link.getLegacyId()).isEqualTo("MRN-1");
        verify(links, never()).saveAndFlush(any());
    }
}
