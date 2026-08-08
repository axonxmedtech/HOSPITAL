package com.hms.service.import_;

import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceUndoTest {

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock PatientRepository patientRepository;
    @Mock OpdRepository opdRepository;
    @Mock BillingRepository billingRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    @InjectMocks ImportBatchService service;

    private ImportBatch completedBatch() {
        ImportBatch b = new ImportBatch();
        b.setId(17L);
        b.setPublicId("pub-17");
        b.setHospitalId(7L);
        b.setEntityType(ImportEntityType.PATIENT);
        b.setStatus(ImportStatus.COMPLETED);
        return b;
    }

    private Patient imported(Long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(7L);
        p.setImportBatchId(17L);
        p.setIsActive(true);
        return p;
    }

    @Test
    void softDeletesImportedPatientsAndMarksTheBatchUndone() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L))
                .thenReturn(Optional.of(completedBatch()));
        when(patientRepository.findByImportBatchId(17L)).thenReturn(List.of(imported(1L), imported(2L)));
        when(opdRepository.countByPatientIdIn(anyList())).thenReturn(0L);
        when(billingRepository.countByPatientIdIn(anyList())).thenReturn(0L);

        ImportBatch result = service.undo("pub-17");

        verify(patientRepository).saveAll(argThat((List<Patient> list) ->
                list.size() == 2 && list.stream().noneMatch(Patient::getIsActive)));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.UNDONE);
        assertThat(result.getUndoneAt()).isNotNull();
        verify(patientRepository, never()).deleteAll(anyList());
    }

    @Test
    void refusesWhenAnImportedPatientHasRealClinicalActivity() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L))
                .thenReturn(Optional.of(completedBatch()));
        when(patientRepository.findByImportBatchId(17L)).thenReturn(List.of(imported(1L)));
        when(opdRepository.countByPatientIdIn(anyList())).thenReturn(3L);

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clinical");

        verify(patientRepository, never()).saveAll(anyList());
    }

    @Test
    void refusesToUndoABatchTwice() {
        ImportBatch b = completedBatch();
        b.setStatus(ImportStatus.UNDONE);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotTouchAnotherHospitalsBatch() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(99L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
