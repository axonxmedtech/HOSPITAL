package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.NotificationService;
import com.hms.service.hospital.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock private PatientRiskAssessmentRepository riskRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private NurseTaskRepository nurseTaskRepository;
    @Mock private DoctorOrderRepository doctorOrderRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private NotificationService notificationService;

    @InjectMocks private RiskAssessmentService riskService;

    @Test
    void evaluateAndSaveRisk_successWithHighRisksSafetyTasksAndReferrals() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setHospitalId(1L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L)).thenReturn(Optional.of(patient));
        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(riskRepository.findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(1L, 200L)).thenReturn(new ArrayList<>());

        when(riskRepository.save(any(PatientRiskAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // High Fall, Pressure Ulcer, and Nutrition input checks
        String inputsJson = "{" +
                "\"age\":78," +
                "\"previous_fall\":true," +
                "\"bedridden\":true," +
                "\"tube_feeding\":true," +
                "\"mobility_status\":\"BEDRIDDEN\"" +
                "}";

        PatientRiskAssessment result = riskService.evaluateAndSaveRisk(100L, 200L, inputsJson, "Alert");

        assertThat(result).isNotNull();
        assertThat(result.getFallRisk()).isEqualTo("HIGH");
        assertThat(result.getPressureUlcerRisk()).isEqualTo("HIGH");
        assertThat(result.getNutritionRisk()).isEqualTo("HIGH");
        assertThat(result.getOverallRisk()).isEqualTo("HIGH");

        // Verify WebSocket broadcast
        verify(notificationService).sendWebSocketRefresh(1L, "PATIENT_RISK_COMPLETED", result.getId());

        // Verify Safety Tasks (e.g. bed rails, turning reminder, hourly check-ins)
        ArgumentCaptor<NurseTask> taskCaptor = ArgumentCaptor.forClass(NurseTask.class);
        verify(nurseTaskRepository, atLeast(4)).save(taskCaptor.capture());
        List<NurseTask> tasks = taskCaptor.getAllValues();
        assertThat(tasks).anyMatch(t -> t.getTaskType().contains("rails"));
        assertThat(tasks).anyMatch(t -> t.getTaskType().contains("turning"));

        // Verify Auto-referrals (Physio + Dietician Referral DoctorOrders)
        ArgumentCaptor<DoctorOrder> orderCaptor = ArgumentCaptor.forClass(DoctorOrder.class);
        verify(doctorOrderRepository, times(2)).save(orderCaptor.capture());
        List<DoctorOrder> orders = orderCaptor.getAllValues();
        assertThat(orders).anyMatch(o -> o.getDescription().contains("Dietician"));
        assertThat(orders).anyMatch(o -> o.getDescription().contains("Physiotherapy"));
    }

    @Test
    void reviewRiskAssessment_doctorNotesSignOff_success() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PatientRiskAssessment risk = new PatientRiskAssessment();
        risk.setId(50L);
        risk.setHospitalId(1L);
        risk.setStatus("COMPLETED");

        Doctor doctor = new Doctor();
        doctor.setId(9L);
        doctor.setName("Dr. Gregory");

        when(riskRepository.findById(50L)).thenReturn(Optional.of(risk));
        when(doctorRepository.findByHospitalIdAndUserId(1L, 10L)).thenReturn(Optional.of(doctor));
        when(riskRepository.save(any(PatientRiskAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientRiskAssessment result = riskService.reviewRiskAssessment(50L, "Looks fine.");

        assertThat(result.getStatus()).isEqualTo("REVIEWED");
        assertThat(result.getReviewedBy()).isEqualTo(9L);
    }
}
