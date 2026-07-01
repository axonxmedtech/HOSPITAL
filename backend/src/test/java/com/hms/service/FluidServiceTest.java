package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.CdssEvaluationService;
import com.hms.service.hospital.FluidService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FluidServiceTest {

    @Mock private FluidIntakeRepository intakeRepository;
    @Mock private FluidOutputRepository outputRepository;
    @Mock private DailyFluidBalanceRepository balanceRepository;
    @Mock private FluidMasterRepository masterRepository;
    @Mock private NurseTaskRepository nurseTaskRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private CdssEvaluationService cdssService;

    @InjectMocks private FluidService fluidService;

    @Test
    void recordIntake_successAndUpdatesBalance() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(masterRepository.countByHospitalId(1L)).thenReturn(0L); // Fallback to standard validation
        when(intakeRepository.save(any(FluidIntake.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FluidIntake result = fluidService.recordIntake(200L, "ORAL", 350, "ORS drink");

        assertThat(result).isNotNull();
        assertThat(result.getVolumeMl()).isEqualTo(350);
        assertThat(result.getType()).isEqualTo("ORAL");
        verify(intakeRepository).save(any(FluidIntake.class));
        verify(balanceRepository).save(any(DailyFluidBalance.class));
    }

    @Test
    void recordIntake_negativeVolume_throwsException() {
        assertThatThrownBy(() -> fluidService.recordIntake(200L, "ORAL", -50, "Juice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fluid volume must be positive");
    }

    @Test
    void recordOutput_safetyGuardrail_throwsException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        assertThatThrownBy(() -> fluidService.recordOutput(200L, "URINE", 4500, "Straw", "Large vol"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds plausible safety limits");
    }

    @Test
    void deriveIvIntakes_success() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));

        NurseTask ivTask = new NurseTask();
        ivTask.setId(77L);
        ivTask.setSource("DOCTOR_ORDER");
        ivTask.setAdministeredQuantity(500.0);
        ivTask.setTaskType("NS Infusion 500ml");
        ivTask.setExecutedAt(LocalDateTime.now());
        ivTask.setExecutedBy(99L);
        ivTask.setStatus("DONE");

        when(nurseTaskRepository.findByIpdAdmissionIdAndStatus(200L, "DONE"))
                .thenReturn(Collections.singletonList(ivTask));
        when(intakeRepository.existsByHospitalIdAndSourceRef(1L, 77L)).thenReturn(false);

        fluidService.deriveIvIntakes(200L);

        verify(intakeRepository).save(any(FluidIntake.class));
    }
}
