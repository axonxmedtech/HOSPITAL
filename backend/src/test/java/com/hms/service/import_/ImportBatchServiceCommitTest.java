package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceCommitTest {

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock PatientRepository patientRepository;
    @Mock OpdRepository opdRepository;
    @Mock BillingRepository billingRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    private ImportBatchService service() {
        ImportEngine engine = new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
        return new ImportBatchService(batchRepository, rowErrorRepository, patientRepository,
                opdRepository, billingRepository, securityHelper, auditLogService, engine);
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients", List.of("Name"),
                List.of(Map.of("Name", "Ramesh"), Map.of("Name", "Sita")));
    }

    @Test
    void createsAPersistedBatchStampedWithTheJwtHospital() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@h.com");
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(false);
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> {
            ImportBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(17L);
            return b;
        });
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportBatch batch = service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx");

        assertThat(batch.getHospitalId()).isEqualTo(7L);
        assertThat(batch.getCreatedBy()).isEqualTo("admin@h.com");
        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getCreatedCount()).isEqualTo(2);
        verify(auditLogService).logAction(eq("IMPORT_COMMIT"), anyString(), eq("admin@h.com"),
                eq(7L), eq("ImportBatch"), eq("17"), isNull());
    }

    @Test
    void refusesASecondConcurrentImportForTheSameHospital() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void marksTheBatchFailedIfTheEngineThrows() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(false);
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> {
            ImportBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(17L);
            return b;
        });
        when(patientRepository.saveAll(anyList())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx"))
                .isInstanceOf(RuntimeException.class);

        verify(batchRepository, atLeastOnce()).save(argThat((ImportBatch b) ->
                b.getStatus() == ImportStatus.FAILED));
    }
}
