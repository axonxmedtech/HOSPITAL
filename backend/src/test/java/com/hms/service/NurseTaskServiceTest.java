package com.hms.service;

import com.hms.entity.NurseTask;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.NurseTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseTaskServiceTest {

    @Mock
    private NurseTaskRepository taskRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @Mock
    private com.hms.service.hospital.MrdService mrdService;

    @InjectMocks
    private NurseTaskService nurseTaskService;

    @Test
    void executeTask_savesMarFieldsForDoneMedication() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        Long taskId = 100L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");

        NurseTask task = new NurseTask();
        task.setId(taskId);
        task.setHospitalId(hospitalId);
        task.setIpdAdmissionId(admissionId);
        task.setStatus("PENDING");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(NurseTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NurseTask executed = nurseTaskService.executeTask(
                admissionId, taskId, "DONE", "Given to patient", 1.0, "IV", "Left Arm", "BP: 120/80", null);

        assertThat(executed.getStatus()).isEqualTo("DONE");
        assertThat(executed.getNotes()).isEqualTo("Given to patient");
        assertThat(executed.getAdministeredQuantity()).isEqualTo(1.0);
        assertThat(executed.getRoute()).isEqualTo("IV");
        assertThat(executed.getInjectionSite()).isEqualTo("Left Arm");
        assertThat(executed.getPreVitals()).isEqualTo("BP: 120/80");
        assertThat(executed.getExecutedByName()).isEqualTo("nurse@hospital.com");
    }

    @Test
    void executeTask_throwsForInvalidStatus() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        assertThatThrownBy(() -> nurseTaskService.executeTask(
                10L, 100L, "INVALID", "Notes", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status must be DONE, SKIPPED, REFUSED, or HELD");
    }

    @Test
    void executeTask_savesMissedReasonForSkippedTask() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        Long taskId = 101L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");

        NurseTask task = new NurseTask();
        task.setId(taskId);
        task.setHospitalId(hospitalId);
        task.setIpdAdmissionId(admissionId);
        task.setStatus("PENDING");
        task.setSource("NURSING");
        task.setTaskType("OBSERVATION");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(NurseTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NurseTask executed = nurseTaskService.executeTask(
                admissionId, taskId, "SKIPPED", "Skipping for now", null, null, null, null, "Patient was sleeping");

        assertThat(executed.getStatus()).isEqualTo("SKIPPED");
        assertThat(executed.getNotes()).isEqualTo("Skipping for now");
        assertThat(executed.getMissedReason()).isEqualTo("Patient was sleeping");
        assertThat(executed.getSource()).isEqualTo("NURSING");
        assertThat(executed.getTaskType()).isEqualTo("OBSERVATION");
    }

    @Test
    void executeTask_throwsUnauthorizedForCrossTenantTask() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        Long taskId = 102L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        NurseTask task = new NurseTask();
        task.setId(taskId);
        task.setHospitalId(2L); // Different hospital
        task.setIpdAdmissionId(admissionId);
        task.setStatus("PENDING");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> nurseTaskService.executeTask(
                admissionId, taskId, "DONE", "Given", null, null, null, null, null))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class)
                .hasMessageContaining("Access denied");
    }
}
