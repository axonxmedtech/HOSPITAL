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

        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(1L);
        ipd.setStatus("DISCHARGED");

        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(ipd));

        assertThatThrownBy(() -> service.planDischarge(1L, new PlanDischargeRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Can only plan discharge for ADMITTED patients");
    }
}
