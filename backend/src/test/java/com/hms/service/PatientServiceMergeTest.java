package com.hms.service;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceMergeTest {

    @Mock PatientRepository patientRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;
    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks PatientService service;

    @Test
    void mergePatients_repointsFKsAndSoftDeletesLoser() {
        Long hospitalId = 1L;
        Long survivorId = 10L;
        Long loserId = 20L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");

        Patient survivor = new Patient();
        survivor.setId(survivorId);
        survivor.setHospitalId(hospitalId);
        survivor.setPublicId("survivor-uuid");
        survivor.setName("Survivor Patient");

        Patient loser = new Patient();
        loser.setId(loserId);
        loser.setHospitalId(hospitalId);
        loser.setPublicId("loser-uuid");
        loser.setName("Loser Patient");

        when(patientRepository.findById(survivorId)).thenReturn(Optional.of(survivor));
        when(patientRepository.findById(loserId)).thenReturn(Optional.of(loser));

        service.mergePatients(survivorId, loserId);

        // Verify child records are repointed — including OPD, consents, EMR history and OT records
        verify(jdbcTemplate, atLeastOnce()).update(contains("appointments"), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("billing"), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("UPDATE opd "), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("patient_consent"), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("patient_diagnosis"), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("patient_implant"), eq(survivorId), eq(loserId));
        verify(jdbcTemplate, atLeastOnce()).update(contains("operation_record"), eq(survivorId), eq(loserId));

        // Verify loser is marked merged and inactive
        verify(patientRepository).save(loser);
        assertThat(loser.getIsMerged()).isTrue();
        assertThat(loser.getMergedToId()).isEqualTo(survivorId);
        assertThat(loser.getIsActive()).isFalse();

        // Verify audit log is recorded
        verify(auditLogService).logAction(
                eq("PATIENT_MERGED"),
                contains("merged"),
                eq("admin@hospital.com"),
                eq(hospitalId),
                eq("PATIENT"),
                eq("survivor-uuid"),
                any()
        );
    }

    @Test
    void mergePatients_rejectsCrossTenantMerge() {
        Long hospitalId = 1L;
        Long survivorId = 10L;
        Long loserId = 20L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        Patient survivor = new Patient();
        survivor.setId(survivorId);
        survivor.setHospitalId(hospitalId);

        Patient foreignLoser = new Patient();
        foreignLoser.setId(loserId);
        foreignLoser.setHospitalId(2L); // Different hospital

        when(patientRepository.findById(survivorId)).thenReturn(Optional.of(survivor));
        when(patientRepository.findById(loserId)).thenReturn(Optional.of(foreignLoser));

        assertThatThrownBy(() -> service.mergePatients(survivorId, loserId))
                .isInstanceOf(RuntimeException.class) // throws RuntimeException due to filter mismatch
                .hasMessageContaining("Duplicate patient not found");

        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    @Test
    void mergePatients_rejectsSelfMerge() {
        // A self-merge would deactivate the surviving patient — must be blocked at service level.
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        assertThatThrownBy(() -> service.mergePatients(10L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("themselves");

        verify(jdbcTemplate, never()).update(anyString(), any(), any());
        verify(patientRepository, never()).save(any());
    }
}
