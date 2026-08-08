package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Identity resolution during import — the decisions that determine whether a row becomes a new
 * patient, updates an existing one, or is refused.
 *
 * <p>Two behaviours here exist to prevent damage rather than to add capability. Reactivation makes
 * the main recovery path work: undo soft-deletes rows but leaves {@code legacy_id} occupying the
 * unique {@code (hospital_id, legacy_id)} index, so without it "undo a mis-mapped import, fix the
 * file, re-import" would fail on a constraint violation at the worst possible moment. And the
 * name+phone fallback refuses to guess: matching on a name alone, or on an ambiguous match, would
 * merge two different people into one record, which no amount of later editing can properly undo.
 */
@ExtendWith(MockitoExtension.class)
class PatientImporterDedupeTest {

    @Mock PatientRepository patientRepository;

    private PatientImporter importer() {
        return new PatientImporter(patientRepository);
    }

    @Test
    void reactivatesASoftDeletedRowFromAnUndoneBatchInsteadOfColliding() {
        Patient softDeleted = new Patient();
        softDeleted.setId(55L);
        softDeleted.setLegacyId("A-9");
        softDeleted.setIsActive(false);
        when(patientRepository.findByHospitalIdAndLegacyId(7L, "A-9")).thenReturn(Optional.of(softDeleted));

        RowOutcome outcome = importer().evaluate(Map.of("Name", "Ramesh", "MRN", "A-9"),
                Map.of("Name", "name", "MRN", "legacyId"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(55L);
    }

    @Test
    void fallsBackToNamePlusPhoneWhenNoMrnColumnIsMapped() {
        Patient existing = new Patient();
        existing.setId(88L);
        when(patientRepository.findByHospitalIdAndNameAndPhone(7L, "Ramesh Patel", "9876543210"))
                .thenReturn(List.of(existing));

        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", "9876543210"),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(88L);
    }

    /**
     * A blank phone degrades the fallback key to the name alone, which is not an identity. Two
     * different people called Sharma must not be collapsed into one record.
     */
    @Test
    void treatsNameOnlyRowsAsNewBecauseNameAloneIsNotAnIdentity() {
        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", ""),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.CREATE);
        assertThat(outcome.patient().getId()).isNull();
    }

    @Test
    void skipsRatherThanMergingWhenTheFallbackMatchesMoreThanOnePatient() {
        Patient a = new Patient();
        a.setId(1L);
        Patient b = new Patient();
        b.setId(2L);
        when(patientRepository.findByHospitalIdAndNameAndPhone(7L, "Ramesh Patel", "9876543210"))
                .thenReturn(List.of(a, b));

        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", "9876543210"),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.SKIP);
        assertThat(outcome.message()).containsIgnoringCase("more than one");
    }

    /**
     * When an MRN column is present it is the identity key outright. The name+phone fallback must
     * not also run, or a row whose MRN is genuinely new could be attached to an unrelated existing
     * patient who happens to share a name and number.
     */
    @Test
    void doesNotFallBackToNamePlusPhoneWhenAnMrnIsPresentButUnmatched() {
        when(patientRepository.findByHospitalIdAndLegacyId(7L, "A-NEW")).thenReturn(Optional.empty());

        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", "9876543210", "MRN", "A-NEW"),
                Map.of("Name", "name", "Mob No", "phone", "MRN", "legacyId"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.CREATE);
        org.mockito.Mockito.verify(patientRepository, org.mockito.Mockito.never())
                .findByHospitalIdAndNameAndPhone(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
