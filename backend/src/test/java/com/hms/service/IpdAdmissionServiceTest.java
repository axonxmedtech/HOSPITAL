package com.hms.service;

import com.hms.dto.PlanDischargeRequest;
import com.hms.entity.Bed;
import com.hms.entity.Billing;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.repository.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.BillingService;
import com.hms.service.hospital.HospitalInventoryService;
import com.hms.service.hospital.IpdAdmissionService;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpdAdmissionServiceTest {

    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock PatientRepository patientRepository;
    @Mock HospitalSettingRepository hospitalSettingRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock WardRepository wardRepository;
    @Mock OpdRepository opdRepository;
    @Mock BedRepository bedRepository;
    @Mock BillingRepository billingRepository;
    @Mock BillingService billingService;
    @Mock AuditLogService auditLogService;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock PrescriptionRepository prescriptionRepository;
    @Mock MedicineRepository medicineRepository;
    @Mock BillingItemRepository billingItemRepository;
    @Mock BillingMedicineRepository billingMedicineRepository;
    @Mock DischargeSummaryRepository dischargeSummaryRepository;
    @Mock BillingPaymentRepository billingPaymentRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock HospitalRepository hospitalRepository;
    @Mock IpdBedHistoryRepository ipdBedHistoryRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock QueueEntryRepository queueEntryRepository;
    @Mock HospitalInventoryRepository hospitalInventoryRepository;
    @Mock HospitalInventoryService hospitalInventoryService;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @Mock com.hms.service.hospital.MrdService mrdService;

    @InjectMocks
    IpdAdmissionService service;

    @Test
    void admitFromOpd_withOccupiedBed_throwsRuntimeException() {
        Opd opd = new Opd();
        opd.setId(1L);
        opd.setPatient(new Patient());

        when(opdRepository.findById(1L)).thenReturn(Optional.of(opd));
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Bed bed = new Bed();
        bed.setBedId(2L);
        bed.setStatus("occupied");

        when(bedRepository.findById(2L)).thenReturn(Optional.of(bed));

        assertThatThrownBy(() -> service.admitFromOpd(1L, 1L, 2L, "ELECTIVE", "Fever"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bed is not available");
    }

    @Test
    void admitFromOpd_withAvailableBed_savesAdmissionAndMarksBedOccupied() {
        Patient patient = new Patient();
        patient.setId(5L);

        Opd opd = new Opd();
        opd.setId(1L);
        opd.setPatient(patient);

        when(opdRepository.findById(1L)).thenReturn(Optional.of(opd));
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@test.com");

        Bed bed = new Bed();
        bed.setBedId(2L);
        bed.setStatus("available");

        when(bedRepository.findById(2L)).thenReturn(Optional.of(bed));
        when(ipdAdmissionRepository.findMaxIpdSequence()).thenReturn(0);

        IpdAdmission savedIpd = new IpdAdmission();
        savedIpd.setId(10L);
        savedIpd.setIpdNumber("IPD-1");
        savedIpd.setPatientId(5L);
        savedIpd.setHospitalId(1L);

        com.hms.entity.Hospital hospital = new com.hms.entity.Hospital();
        hospital.setId(1L);
        hospital.setModules(java.util.List.of("BILLING"));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(hospital));
        when(ipdAdmissionRepository.save(any(IpdAdmission.class))).thenReturn(savedIpd);
        when(billingRepository.save(any(Billing.class))).thenAnswer(i -> i.getArguments()[0]);
        when(ipdBedHistoryRepository.save(any(com.hms.entity.IpdBedHistory.class))).thenAnswer(i -> i.getArguments()[0]);
        when(wardRepository.findById(1L)).thenReturn(Optional.empty());
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(any(), any()))
                .thenReturn(List.of());

        IpdAdmission result = service.admitFromOpd(1L, 1L, 2L, "ELECTIVE", "Fever");

        assertThat(result).isNotNull();

        ArgumentCaptor<Bed> bedCaptor = ArgumentCaptor.forClass(Bed.class);
        verify(bedRepository).save(bedCaptor.capture());
        assertThat(bedCaptor.getValue().getStatus()).isEqualTo("occupied");
    }

    @Test
    void planDischarge_withNonDoctorRole_throwsAccessDeniedException() {
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");

        assertThatThrownBy(() -> service.planDischarge(1L, new PlanDischargeRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void planDischarge_whenPatientNotAdmitted_throwsRuntimeException() {
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(1L);
        ipd.setHospitalId(1L);
        ipd.setStatus("DISCHARGED");

        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(ipd));

        assertThatThrownBy(() -> service.planDischarge(1L, new PlanDischargeRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Can only plan discharge for ADMITTED");

    }

    @Test
    void planDischarge_rejectsCrossTenantAdmission() {
        Long ipdId = 500L;
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission foreignAdmission = new IpdAdmission();
        foreignAdmission.setId(ipdId);
        foreignAdmission.setHospitalId(2L);
        foreignAdmission.setStatus("ADMITTED");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(foreignAdmission));

        PlanDischargeRequest req = new PlanDischargeRequest();

        assertThatThrownBy(() -> service.planDischarge(ipdId, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tenant mismatch");

        verify(dischargeSummaryRepository, never()).save(any(com.hms.entity.DischargeSummary.class));
    }

    @Test
    void confirmDischarge_rejectsCrossTenantAdmission() {
        Long ipdId = 501L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission foreignAdmission = new IpdAdmission();
        foreignAdmission.setId(ipdId);
        foreignAdmission.setHospitalId(2L);
        foreignAdmission.setStatus("DISCHARGE_PLANNED");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(foreignAdmission));

        assertThatThrownBy(() -> service.confirmDischarge(ipdId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tenant mismatch");

        verify(hospitalSettingRepository, never()).findByHospital_Id(any());
    }

    @Test
    void planDischarge_stampsTenantPatientDoctorOnSummary() {
        Long ipdId = 502L;
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(ipdId);
        admission.setHospitalId(7L);
        admission.setPatientId(42L);
        admission.setDoctorId(9L);
        admission.setStatus("ADMITTED");
        admission.setIpdNumber("IPD-7-001");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(admission));

        PlanDischargeRequest req = new PlanDischargeRequest();
        req.setFinalDiagnosis("Recovered");

        service.planDischarge(ipdId, req);

        ArgumentCaptor<com.hms.entity.DischargeSummary> captor =
                ArgumentCaptor.forClass(com.hms.entity.DischargeSummary.class);
        verify(dischargeSummaryRepository).save(captor.capture());
        com.hms.entity.DischargeSummary saved = captor.getValue();
        assertThat(saved.getHospitalId()).isEqualTo(7L);
        assertThat(saved.getPatientId()).isEqualTo(42L);
        assertThat(saved.getDoctorId()).isEqualTo(9L);
        assertThat(saved.getIpdAdmissionId()).isEqualTo(ipdId);
    }

    @Test
    void admitFromEmergency_savesAdmissionAndMarksBedOccupied() {
        Long patientId = 5L;
        Long doctorId = 9L;
        Long wardId = 1L;
        Long bedId = 2L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(patientId);
        patient.setHospitalId(1L);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        Bed bed = new Bed();
        bed.setBedId(bedId);
        bed.setStatus("available");
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));

        IpdAdmission savedAdmission = new IpdAdmission();
        savedAdmission.setId(101L);
        savedAdmission.setIpdNumber("IPD-1");
        savedAdmission.setPatientId(patientId);
        savedAdmission.setDoctorId(doctorId);
        savedAdmission.setHospitalId(1L);
        savedAdmission.setWardId(wardId);
        savedAdmission.setBedId(bedId);
        when(ipdAdmissionRepository.save(any(IpdAdmission.class))).thenReturn(savedAdmission);

        IpdAdmission result = service.admitFromEmergency(patientId, doctorId, wardId, bedId, "EMERGENCY", "Fever");

        assertThat(result).isNotNull();
        assertThat(result.getIpdNumber()).isEqualTo("IPD-1");
        assertThat(result.getPatientId()).isEqualTo(patientId);
        assertThat(bed.getStatus()).isEqualTo("occupied");
        verify(bedRepository).save(bed);
    }

    @Test
    void admitFromEmergency_withMismatchedPatientTenant_throwsRuntimeException() {
        Long patientId = 5L;
        Long doctorId = 9L;
        Long wardId = 1L;
        Long bedId = 2L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(patientId);
        patient.setHospitalId(2L); // Different hospital ID
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        Bed bed = new Bed();
        bed.setBedId(bedId);
        bed.setStatus("available");
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));

        assertThatThrownBy(() -> service.admitFromEmergency(patientId, doctorId, wardId, bedId, "EMERGENCY", "Fever"))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class)
                .hasMessageContaining("Patient does not belong to this hospital");
    }

    @Test
    void addIpdFollowup_rejectsCrossTenantAdmission() {
        Long ipdId = 501L;
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission foreignAdmission = new IpdAdmission();
        foreignAdmission.setId(ipdId);
        foreignAdmission.setHospitalId(2L); // Different hospital ID
        foreignAdmission.setStatus("ADMITTED");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(foreignAdmission));

        assertThatThrownBy(() -> service.addIpdFollowup(ipdId, "Fever", "Give Paracetamol", null))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class)
                .hasMessageContaining("Access Denied: Record belongs to another tenant");

        verify(medicalRecordRepository, never()).save(any());
    }
}
