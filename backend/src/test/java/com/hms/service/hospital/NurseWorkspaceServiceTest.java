package com.hms.service.hospital;

import com.hms.dto.MyPatientDTO;
import com.hms.dto.NursePatientDetailDTO;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseWorkspaceServiceTest {

    @Mock PatientNurseAssignmentRepository assignmentRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock PatientRepository patientRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock WardRepository wardRepository;
    @Mock BedRepository bedRepository;
    @Mock PrescriptionRepository prescriptionRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock BillingRepository billingRepository;
    @Mock BillingPaymentRepository billingPaymentRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock NurseAccessGuard nurseAccessGuard;

    @InjectMocks NurseWorkspaceService service;

    private PatientNurseAssignment assignment(Long admissionId, Long hospitalId) {
        PatientNurseAssignment a = new PatientNurseAssignment();
        a.setIpdAdmissionId(admissionId);
        a.setHospitalId(hospitalId);
        a.setNurseUserId(20L);
        a.setIsActive(true);
        return a;
    }

    private IpdAdmission admission(Long id, Long hospitalId, String status) {
        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(id);
        ipd.setHospitalId(hospitalId);
        ipd.setStatus(status);
        ipd.setIpdNumber("IPD-" + id);
        ipd.setPatientId(100L + id);
        ipd.setDoctorId(300L);
        return ipd;
    }

    @Test
    void getMyPatients_excludesDischargedAdmissions() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserId()).thenReturn(20L);
        when(assignmentRepository.findByNurseUserIdAndIsActiveTrue(20L))
                .thenReturn(List.of(assignment(1L, 7L), assignment(2L, 7L)));
        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(admission(1L, 7L, "ADMITTED")));
        when(ipdAdmissionRepository.findById(2L)).thenReturn(Optional.of(admission(2L, 7L, "DISCHARGED")));
        when(patientRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(doctorRepository.findById(anyLong())).thenReturn(Optional.empty());

        List<MyPatientDTO> result = service.getMyPatients();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIpdNumber()).isEqualTo("IPD-1");
    }

    @Test
    void getMyPatients_excludesOtherHospitalAssignments() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserId()).thenReturn(20L);
        when(assignmentRepository.findByNurseUserIdAndIsActiveTrue(20L))
                .thenReturn(List.of(assignment(9L, 99L))); // different hospital

        List<MyPatientDTO> result = service.getMyPatients();

        assertThat(result).isEmpty();
        verify(ipdAdmissionRepository, never()).findById(9L);
    }

    @Test
    void getPatientDetail_deniedWhenNotAssigned() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        doThrow(new AccessDeniedException("not assigned")).when(nurseAccessGuard).assertAssigned(5L);

        assertThatThrownBy(() -> service.getPatientDetail(5L))
                .isInstanceOf(AccessDeniedException.class);
        verify(ipdAdmissionRepository, never()).findById(5L);
    }

    @Test
    void getPatientDetail_buildsCompositeWhenAssigned() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        doNothing().when(nurseAccessGuard).assertAssigned(1L);
        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(admission(1L, 7L, "ADMITTED")));
        when(patientRepository.findById(101L)).thenReturn(Optional.empty());
        when(doctorRepository.findById(300L)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        Prescription pr = new Prescription();
        pr.setMedicineName("Paracetamol");
        pr.setDosage("500mg");
        pr.setStatus("ACTIVE");
        when(prescriptionRepository.findByIpdAdmissionIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(pr));
        when(billingRepository.findByIpdAdmissionId(1L)).thenReturn(List.of());

        NursePatientDetailDTO dto = service.getPatientDetail(1L);

        assertThat(dto.getIpdNumber()).isEqualTo("IPD-1");
        assertThat(dto.getPrescriptions()).hasSize(1);
        assertThat(dto.getPrescriptions().get(0).getMedicineName()).isEqualTo("Paracetamol");
        assertThat(dto.getBilling().getBalance()).isEqualByComparingTo("0");
    }
}
