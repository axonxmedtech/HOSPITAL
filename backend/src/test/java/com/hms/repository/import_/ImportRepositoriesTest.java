package com.hms.repository.import_;

import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowResult;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JPA side of the phase-1 foundation: the entity mappings agree with the migrations on the
 * constraints that matter, and every application-facing repository lookup is hospital-scoped.
 * Constraint behaviour on real MySQL (cascades, the V21 index) is {@code ImportSchemaFoundationIT}'s
 * job; this slice runs on the H2 test profile and covers what the mapping alone guarantees.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ImportRepositoriesTest {

    @Autowired ImportBatchRepository batches;
    @Autowired ImportRowResultRepository results;
    @Autowired PatientImportLinkRepository links;

    private static final long HOSPITAL_A = 11L;
    private static final long HOSPITAL_B = 22L;

    @Test
    void aBatchGetsAPublicIdOnPersistAndIsOnlyVisibleToItsOwnHospital() {
        ImportBatch batch = batches.save(newBatch(HOSPITAL_A, ImportStatus.COMPLETED, "a".repeat(64)));

        assertThat(batch.getPublicId()).isNotBlank();
        assertThat(batches.findByPublicIdAndHospitalId(batch.getPublicId(), HOSPITAL_A)).isPresent();
        assertThat(batches.findByPublicIdAndHospitalId(batch.getPublicId(), HOSPITAL_B)).isEmpty();
        assertThat(batches.findByHospitalIdOrderByCreatedAtDesc(HOSPITAL_B)).isEmpty();
    }

    @Test
    void theFingerprintGuardSeesCompletedAndPartialButNotFailedOrUndoneRuns() {
        String sha = "b".repeat(64);
        batches.save(newBatch(HOSPITAL_A, ImportStatus.FAILED, sha));
        batches.save(newBatch(HOSPITAL_A, ImportStatus.UNDONE, sha));

        assertThat(batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(
                HOSPITAL_A, sha, ImportStatus.ALREADY_IMPORTED)).isEmpty();

        ImportBatch partial = batches.save(newBatch(HOSPITAL_A, ImportStatus.PARTIAL, sha));

        assertThat(batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(
                HOSPITAL_A, sha, ImportStatus.ALREADY_IMPORTED)).contains(partial);
        // Same bytes uploaded by another tenant are that tenant's business.
        assertThat(batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(
                HOSPITAL_B, sha, ImportStatus.ALREADY_IMPORTED)).isEmpty();
    }

    @Test
    void staleRunningBatchesAreFoundByHeartbeatNotByCreationTime() {
        ImportBatch stale = newBatch(HOSPITAL_A, ImportStatus.RUNNING, "c".repeat(64));
        stale.setHeartbeatAt(LocalDateTime.now().minusMinutes(45));
        ImportBatch live = newBatch(HOSPITAL_A, ImportStatus.RUNNING, "d".repeat(64));
        live.setHeartbeatAt(LocalDateTime.now().minusMinutes(1));
        batches.saveAll(List.of(stale, live));

        List<ImportBatch> abandoned = batches.findByHospitalIdAndStatusAndHeartbeatAtBefore(
                HOSPITAL_A, ImportStatus.RUNNING, LocalDateTime.now().minusMinutes(30));

        assertThat(abandoned).containsExactly(stale);
        assertThat(batches.existsByHospitalIdAndStatus(HOSPITAL_A, ImportStatus.RUNNING)).isTrue();
        assertThat(batches.existsByHospitalIdAndStatus(HOSPITAL_B, ImportStatus.RUNNING)).isFalse();
    }

    @Test
    void rowResultsComeBackInSpreadsheetOrderAndFilterByState() {
        ImportBatch batch = batches.save(newBatch(HOSPITAL_A, ImportStatus.PARTIAL, "e".repeat(64)));
        results.save(new ImportRowResult(batch.getId(), 5, ImportRowState.FAILED, ImportReasonCode.INVALID_DOB));
        results.save(new ImportRowResult(batch.getId(), 2, ImportRowState.NEEDS_REVIEW, ImportReasonCode.PHONE_MISSING));
        results.save(new ImportRowResult(batch.getId(), 3, ImportRowState.CREATED, null));

        assertThat(results.findByBatchIdOrderByRowNumAsc(batch.getId()))
                .extracting(ImportRowResult::getRowNum).containsExactly(2, 3, 5);
        assertThat(results.findByBatchIdAndStateInOrderByRowNumAsc(batch.getId(),
                List.of(ImportRowState.NEEDS_REVIEW, ImportRowState.FAILED), PageRequest.of(0, 10)))
                .extracting(ImportRowResult::getReasonCode)
                .containsExactly(ImportReasonCode.PHONE_MISSING, ImportReasonCode.INVALID_DOB);
        assertThat(results.countByBatchIdAndState(batch.getId(), ImportRowState.CREATED)).isEqualTo(1);
    }

    @Test
    void legacyIdIsUniquePerHospitalNullsRepeatAndTenantsAreIndependent() {
        links.save(newLink(1L, HOSPITAL_A, null, 100L));
        links.save(newLink(2L, HOSPITAL_A, null, 100L));
        links.save(newLink(3L, HOSPITAL_A, "MRN-1", 100L));
        links.save(newLink(4L, HOSPITAL_B, "MRN-1", 200L));
        links.flush();

        assertThat(links.findByHospitalIdAndLegacyId(HOSPITAL_A, "MRN-1")).map(PatientImportLink::getPatientId).contains(3L);
        assertThat(links.findByHospitalIdAndLegacyId(HOSPITAL_B, "MRN-1")).map(PatientImportLink::getPatientId).contains(4L);
        assertThat(links.findByHospitalIdAndPatientId(HOSPITAL_B, 3L)).isEmpty();
        assertThat(links.findByHospitalIdAndCreatedByBatchId(HOSPITAL_A, 100L)).hasSize(3);
        assertThat(links.countByHospitalIdAndCreatedByBatchId(HOSPITAL_B, 100L)).isZero();

        assertThatThrownBy(() -> links.saveAndFlush(newLink(5L, HOSPITAL_A, "MRN-1", 100L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPatientHasAtMostOneLink() {
        links.saveAndFlush(newLink(7L, HOSPITAL_A, "MRN-7", 100L));

        assertThatThrownBy(() -> links.saveAndFlush(newLink(7L, HOSPITAL_A, "MRN-8", 100L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ImportBatch newBatch(long hospitalId, ImportStatus status, String sha) {
        ImportBatch b = new ImportBatch();
        b.setHospitalId(hospitalId);
        b.setFileSha256(sha);
        b.setStatus(status);
        b.setCreatedBy("admin@test");
        return b;
    }

    private static PatientImportLink newLink(long patientId, long hospitalId, String legacyId, long batchId) {
        PatientImportLink l = new PatientImportLink();
        l.setPatientId(patientId);
        l.setHospitalId(hospitalId);
        l.setLegacyId(legacyId);
        l.setCreatedByBatchId(batchId);
        l.setLastBatchId(batchId);
        l.setLastImportedAt(LocalDateTime.now());
        return l;
    }
}
