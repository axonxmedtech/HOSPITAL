package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.ClinicalAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ClinicalAssessmentServiceTest {

    @Mock private ClinicalAssessmentRepository clinicalAssessmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private VitalSignsRepository vitalSignsRepository;
    @Mock private DoctorOrderRepository doctorOrderRepository;
    @Mock private PatientDiagnosisRepository patientDiagnosisRepository;
    @Mock private PatientMedicalHistoryRepository patientMedicalHistoryRepository;
    @Mock private PatientSurgicalHistoryRepository patientSurgicalHistoryRepository;
    @Mock private PatientMedicationHistoryRepository patientMedicationHistoryRepository;
    @Mock private PatientFamilyHistoryRepository patientFamilyHistoryRepository;
    @Mock private PatientSocialHistoryRepository patientSocialHistoryRepository;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks private ClinicalAssessmentService clinicalAssessmentService;

    @Test
    void createDraft_successAndVitalsImport() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);
        admission.setDoctorId(5L);

        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(clinicalAssessmentRepository.findFirstByHospitalIdAndAdmissionIdAndStatusNotInOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        Doctor doctor = new Doctor();
        doctor.setId(5L);
        doctor.setName("Dr. House");
        when(doctorRepository.findByHospitalIdAndUserId(1L, 10L)).thenReturn(Optional.of(doctor));

        VitalSigns vitals = new VitalSigns();
        vitals.setTemperature(new java.math.BigDecimal("37.2"));
        vitals.setPulse(78);
        vitals.setBloodPressure("120/80");
        vitals.setSpo2(98);
        when(vitalSignsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(200L))
                .thenReturn(Collections.singletonList(vitals));

        ClinicalAssessment saved = new ClinicalAssessment();
        saved.setId(900L);
        saved.setStatus("DRAFT");
        saved.setChiefComplaint("Latest Vitals: Temp: 37.2 C, Pulse: 78 bpm, BP: 120/80, SpO2: 98%");

        when(clinicalAssessmentRepository.save(any(ClinicalAssessment.class))).thenReturn(saved);

        ClinicalAssessment result = clinicalAssessmentService.createDraft(200L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getChiefComplaint()).contains("Temp: 37.2");
        verify(clinicalAssessmentRepository).save(any(ClinicalAssessment.class));
    }

    @Test
    void finalizeAssessment_assignedDoctorValidation_successAndOrdersSpawning() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        ClinicalAssessment assessment = new ClinicalAssessment();
        assessment.setId(900L);
        assessment.setHospitalId(1L);
        assessment.setPatientId(100L);
        assessment.setAdmissionId(200L);
        assessment.setStatus("DRAFT");
        assessment.setProvisionalDiagnosis("Acute Bronchitis");

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);
        admission.setDoctorId(5L); // Admitting doctor ID is 5

        Doctor doctor = new Doctor();
        doctor.setId(5L); // Match
        doctor.setName("Dr. House");

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setMedicalHistory("Diabetes, Hypertension"); // legacy medical history text

        when(clinicalAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));
        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(doctorRepository.findByHospitalIdAndUserId(1L, 10L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L)).thenReturn(Optional.of(patient));

        when(clinicalAssessmentRepository.save(any(ClinicalAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorOrder spawnedOrder = new DoctorOrder();
        spawnedOrder.setOrderType("INVESTIGATION");
        spawnedOrder.setDescription("Chest X-Ray");

        ClinicalAssessment result = clinicalAssessmentService.finalizeAssessment(
                900L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                Collections.singletonList(spawnedOrder)
        );

        assertThat(result.getStatus()).isEqualTo("FINALIZED");
        assertThat(result.getFinalizedBy()).isEqualTo(5L);

        // Verify EMR legacy string split migration
        verify(patientMedicalHistoryRepository, times(2)).save(any(PatientMedicalHistory.class));
        assertThat(patient.getMedicalHistory()).isNull(); // cleared

        // Verify provisional diagnosis timeline append
        verify(patientDiagnosisRepository).save(any(PatientDiagnosis.class));

        // Verify downstream doctor order creation
        verify(doctorOrderRepository).save(any(DoctorOrder.class));
    }

    @Test
    void finalizeAssessment_nonAssignedDoctor_throwsUnauthorized() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        ClinicalAssessment assessment = new ClinicalAssessment();
        assessment.setId(900L);
        assessment.setHospitalId(1L);
        assessment.setAdmissionId(200L);
        assessment.setStatus("DRAFT");

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setDoctorId(99L); // Admitting doctor ID is 99

        Doctor doctor = new Doctor();
        doctor.setId(5L); // Logged in doctor ID is 5 (Mismatch!)

        when(clinicalAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));
        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(doctorRepository.findByHospitalIdAndUserId(1L, 10L)).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> clinicalAssessmentService.finalizeAssessment(900L, null, null, null, null, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Only the admitting doctor/consultant is authorized");
    }

    @Test
    void amendAssessment_locksParentAndIncrementsVersion() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        ClinicalAssessment parent = new ClinicalAssessment();
        parent.setId(900L);
        parent.setHospitalId(1L);
        parent.setPatientId(100L);
        parent.setAdmissionId(200L);
        parent.setStatus("FINALIZED");
        parent.setVersion(1);

        Doctor doctor = new Doctor();
        doctor.setId(5L);

        when(clinicalAssessmentRepository.findById(900L)).thenReturn(Optional.of(parent));
        when(doctorRepository.findByHospitalIdAndUserId(1L, 10L)).thenReturn(Optional.of(doctor));
        when(clinicalAssessmentRepository.save(any(ClinicalAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalAssessment result = clinicalAssessmentService.amendAssessment(900L, "New complaint", "New HPI", "New Dx", "New plan");

        assertThat(parent.getStatus()).isEqualTo("AMENDED");
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getParentId()).isEqualTo(900L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }
}
