package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportEngineCommitTest {

    @Mock PatientRepository patientRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;

    private ImportEngine engine() {
        return new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
    }

    private ImportBatch batch() {
        ImportBatch b = new ImportBatch();
        b.setId(17L);
        b.setHospitalId(7L);
        b.setEntityType(ImportEntityType.PATIENT);
        return b;
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients", List.of("Name"),
                List.of(Map.of("Name", "Ramesh"), Map.of("Name", ""), Map.of("Name", "Sita")));
    }

    @Test
    void stampsEverySavedRowWithTheBatchIdAndHospitalFromTheJwt() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        // Two saves per chunk: the insert, then the customId assignment that needs the generated
        // id. Assert on the first, which is the one carrying the lineage stamps.
        ArgumentCaptor<List<Patient>> captor = ArgumentCaptor.forClass(List.class);
        verify(patientRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0)).allSatisfy(p -> {
            assertThat(p.getImportBatchId()).isEqualTo(17L);
            assertThat(p.getHospitalId()).isEqualTo(7L);
            assertThat(p.getSource()).isEqualTo(ImportSource.IMPORTED);
        });
    }

    @Test
    void oneBadRowDoesNotStopTheRest() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportBatch b = batch();
        engine().commit(sheet(), Map.of("Name", "name"), b);

        assertThat(b.getCreatedCount()).isEqualTo(2);
        assertThat(b.getFailedCount()).isEqualTo(1);
        assertThat(b.getStatus()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void failedRowsArePersistedAsRowErrors() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<ImportRowError>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowErrorRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRowNumber()).isEqualTo(3);
        assertThat(captor.getValue().get(0).getRawRowJson()).contains("Name");
    }

    /**
     * The row number on the persisted ImportRowError is what ends up in the downloadable error
     * CSV, so it must match the spreadsheet row exactly as the preview reported it — a number
     * that is right in the dry-run preview and wrong in the CSV would be worse than being wrong
     * in both, since the admin would trust it while editing the wrong line.
     */
    @Test
    void persistedRowErrorNumberMatchesTheSpreadsheetRowFromThePreview() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportEngine engine = engine();
        var preview = engine.dryRun(sheet(), Map.of("Name", "name"), 7L);
        engine.commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<ImportRowError>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowErrorRepository).saveAll(captor.capture());

        assertThat(preview.errors()).hasSize(1);
        assertThat(captor.getValue().get(0).getRowNumber())
                .isEqualTo(preview.errors().get(0).rowNumber());
    }

    /**
     * customId is the PAT&lt;id&gt; number reception sees in patient lists, search and on case papers.
     * PatientService assigns it after the insert because it derives from the auto-increment id; the
     * importer bypasses that service, so without this step every imported patient would carry a
     * null patient number and render blank throughout the UI.
     */
    @Test
    void assignsThePatientNumberToImportedRowsAfterInsert() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Patient> batchRows = inv.getArgument(0);
            long id = 100L;
            for (Patient p : batchRows) {
                if (p.getId() == null) p.setId(id++);
            }
            return batchRows;
        });

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<Patient>> captor = ArgumentCaptor.forClass(List.class);
        verify(patientRepository, org.mockito.Mockito.atLeast(2)).saveAll(captor.capture());

        List<Patient> lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSave).isNotEmpty();
        assertThat(lastSave).allSatisfy(p ->
                assertThat(p.getCustomId()).isEqualTo("PAT" + p.getId()));
    }

    /** An existing patient keeps the number they already have; re-import must not churn it. */
    @Test
    void doesNotReassignThePatientNumberOfAnExistingRecord() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Patient> batchRows = inv.getArgument(0);
            for (Patient p : batchRows) {
                if (p.getId() == null) p.setId(200L);
                if (p.getCustomId() == null) p.setCustomId("PAT-ALREADY-SET");
            }
            return batchRows;
        });

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<Patient>> captor = ArgumentCaptor.forClass(List.class);
        verify(patientRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0))
                .allSatisfy(p -> assertThat(p.getCustomId()).doesNotStartWith("PAT200"));
    }

    /**
     * A chunk failing to save must not swallow the run's error report or leave the batch claiming
     * nothing happened. Before this was fixed, a crash mid-commit propagated with the batch still
     * at RUNNING, all counts zero, and every accumulated ImportRowError discarded — so an admin
     * whose import had already written thousands of rows would be shown no report at all and no
     * indication of how far it got.
     */
    @Test
    void aFailedChunkStillRecordsTheErrorsAndCountsGatheredSoFar() {
        when(patientRepository.saveAll(anyList())).thenThrow(new RuntimeException("db went away"));

        ImportBatch b = batch();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> engine().commit(sheet(), Map.of("Name", "name"), b))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db went away");

        // The row error collected before the crash survived.
        ArgumentCaptor<List<ImportRowError>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowErrorRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);

        // Counts reflect what was actually persisted: nothing, because the only save threw.
        assertThat(b.getCreatedCount()).isZero();
        assertThat(b.getFailedCount()).isEqualTo(1);
        // Never left claiming success.
        assertThat(b.getStatus()).isNotEqualTo(ImportStatus.COMPLETED);
    }
}
