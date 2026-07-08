package com.hms.service.hospital;

import com.hms.dto.CreateSurgeryRequest;
import com.hms.dto.ScheduleSurgeryRequest;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurgeryServiceTest {

    @Mock SurgeryRepository surgeryRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;
    @Mock WardRepository wardRepository;
    @Mock BedRepository bedRepository;
    @Mock PatientRepository patientRepository;
    @Mock PatientNurseAssignmentRepository assignmentRepository;
    @Mock NotificationService notificationService;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    @InjectMocks SurgeryService service;

    private IpdAdmission admission() {
        IpdAdmission a = new IpdAdmission();
        a.setId(1L); a.setHospitalId(7L); a.setPatientId(500L); a.setIpdNumber("IPD-1");
        return a;
    }

    private CreateSurgeryRequest createReq() {
        CreateSurgeryRequest r = new CreateSurgeryRequest();
        r.setIpdAdmissionId(1L);
        r.setProcedureName("Appendectomy");
        r.setPriority("ELECTIVE");
        return r;
    }

    @Test
    void createRequest_savesRequestedSurgery() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserId()).thenReturn(30L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doc@x.com");
        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(admission()));
        Doctor d = new Doctor(); d.setId(11L); d.setHospitalId(7L);
        when(doctorRepository.findByEmailAndHospitalId("doc@x.com", 7L)).thenReturn(Optional.of(d));
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(eq(1L), any())).thenReturn(List.of());
        when(surgeryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Surgery saved = service.createRequest(createReq());

        assertThat(saved.getStatus()).isEqualTo(Surgery.REQUESTED);
        assertThat(saved.getProcedureName()).isEqualTo("Appendectomy");
        assertThat(saved.getRequestedByDoctorId()).isEqualTo(11L);
        assertThat(saved.getPatientId()).isEqualTo(500L);
    }

    @Test
    void createRequest_rejectsSecondActiveSurgery() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(admission()));
        Surgery existing = new Surgery(); existing.setStatus(Surgery.REQUESTED);
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(eq(1L), any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createRequest(createReq()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active surgery");
    }

    @Test
    void schedule_setsScheduledAndNotifiesNurse() {
        Surgery s = new Surgery();
        s.setId(9L); s.setHospitalId(7L); s.setIpdAdmissionId(1L); s.setStatus(Surgery.REQUESTED);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserId()).thenReturn(40L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));
        Doctor surgeon = new Doctor(); surgeon.setId(11L); surgeon.setHospitalId(7L);
        // Any doctor (not just surgeons) can be assigned as the operating surgeon.
        surgeon.setSpecialization("Cardiologist"); surgeon.setEmail("surg@x.com"); surgeon.setName("Dr Surg");
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(11L, 7L)).thenReturn(Optional.of(surgeon));
        Ward ward = new Ward(); ward.setWardId(3L); ward.setHospitalId(7L); ward.setWardName("OT");
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward));
        PatientNurseAssignment asg = new PatientNurseAssignment(); asg.setNurseUserId(77L);
        when(assignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(1L)).thenReturn(Optional.of(asg));
        when(ipdAdmissionRepository.findById(1L)).thenReturn(Optional.of(admission()));
        when(patientRepository.findById(500L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("surg@x.com")).thenReturn(Optional.empty());
        when(surgeryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleSurgeryRequest req = new ScheduleSurgeryRequest();
        req.setSurgeonDoctorId(11L);
        req.setScheduledAt(LocalDateTime.now().plusDays(1));
        req.setOtWardId(3L);

        Surgery out = service.schedule("s-pub", req);

        assertThat(out.getStatus()).isEqualTo(Surgery.SCHEDULED);
        assertThat(out.getSurgeonDoctorId()).isEqualTo(11L);
        verify(notificationService).create(eq(77L), eq(7L), eq("OT_SCHEDULED"), any(), any(), any(), any());
    }

    @Test
    void schedule_allowsOtherOperator_withFreeTextName() {
        Surgery s = new Surgery();
        s.setId(9L); s.setHospitalId(7L); s.setIpdAdmissionId(1L); s.setStatus(Surgery.REQUESTED);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserId()).thenReturn(40L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));
        Ward ward = new Ward(); ward.setWardId(3L); ward.setHospitalId(7L); ward.setWardName("OT");
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward));
        when(assignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(1L)).thenReturn(Optional.empty());
        when(surgeryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduleSurgeryRequest req = new ScheduleSurgeryRequest();
        req.setSurgeonName("Dr. Visiting Anaesthetist");   // no surgeonDoctorId → "Other"
        req.setScheduledAt(LocalDateTime.now().plusDays(1));
        req.setOtWardId(3L);

        Surgery out = service.schedule("s-pub", req);

        assertThat(out.getStatus()).isEqualTo(Surgery.SCHEDULED);
        assertThat(out.getSurgeonDoctorId()).isNull();
        assertThat(out.getSurgeonName()).isEqualTo("Dr. Visiting Anaesthetist");
    }

    @Test
    void schedule_rejectsOtherWithoutName() {
        Surgery s = new Surgery();
        s.setId(9L); s.setHospitalId(7L); s.setIpdAdmissionId(1L); s.setStatus(Surgery.REQUESTED);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));

        ScheduleSurgeryRequest req = new ScheduleSurgeryRequest();  // no doctor id, no name
        req.setScheduledAt(LocalDateTime.now().plusDays(1));
        req.setOtWardId(3L);

        assertThatThrownBy(() -> service.schedule("s-pub", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator's name");
    }

    @Test
    void start_requiresAvailableOtBed_thenMarksOccupied() {
        Surgery s = new Surgery();
        s.setId(9L); s.setHospitalId(7L); s.setIpdAdmissionId(1L); s.setStatus(Surgery.SCHEDULED); s.setOtWardId(3L);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));
        Bed bed = new Bed(); bed.setBedId(50L); bed.setHospitalId(7L); bed.setWardId(3L); bed.setStatus("available");
        when(bedRepository.findByWardIdAndHospitalId(3L, 7L)).thenReturn(List.of(bed));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(surgeryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Surgery out = service.start("s-pub");

        assertThat(out.getStatus()).isEqualTo(Surgery.IN_PROGRESS);
        assertThat(out.getStartedAt()).isNotNull();
        assertThat(bed.getStatus()).isEqualTo("occupied");
    }

    @Test
    void complete_freesOtBed() {
        Surgery s = new Surgery();
        s.setId(9L); s.setHospitalId(7L); s.setIpdAdmissionId(1L); s.setStatus(Surgery.IN_PROGRESS);
        s.setOtWardId(3L); s.setOtBedId(50L);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));
        Bed bed = new Bed(); bed.setBedId(50L); bed.setHospitalId(7L); bed.setWardId(3L); bed.setStatus("occupied");
        when(bedRepository.findById(50L)).thenReturn(Optional.of(bed));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(surgeryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Surgery out = service.complete("s-pub");

        assertThat(out.getStatus()).isEqualTo(Surgery.COMPLETED);
        assertThat(out.getCompletedAt()).isNotNull();
        assertThat(bed.getStatus()).isEqualTo("available");
    }

    @Test
    void schedule_crossTenant_denied() {
        Surgery s = new Surgery(); s.setHospitalId(999L); s.setStatus(Surgery.REQUESTED);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(surgeryRepository.findByPublicId("s-pub")).thenReturn(Optional.of(s));

        ScheduleSurgeryRequest req = new ScheduleSurgeryRequest();
        req.setSurgeonDoctorId(11L); req.setScheduledAt(LocalDateTime.now()); req.setOtWardId(3L);

        assertThatThrownBy(() -> service.schedule("s-pub", req))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class);
    }
}
