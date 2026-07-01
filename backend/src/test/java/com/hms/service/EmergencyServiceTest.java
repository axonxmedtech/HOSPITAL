package com.hms.service;

import com.hms.dto.EmergencyVisitRequest;
import com.hms.entity.EmergencyVisit;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Patient;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.EmergencyVisitRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.EmergencyService;
import com.hms.service.hospital.IpdAdmissionService;
import com.hms.service.hospital.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock private EmergencyVisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PatientService patientService;
    @Mock private IpdAdmissionService ipdAdmissionService;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private HospitalWebSocketHandler webSocketHandler;

    @InjectMocks
    private EmergencyService service;

    private EmergencyVisit openVisit(Long id, Long hospitalId, String status, String triageLevel) {
        EmergencyVisit v = new EmergencyVisit();
        v.setId(id);
        v.setHospitalId(hospitalId);
        v.setPatientId(50L);
        v.setEmergencyNumber("ER-1");
        v.setStatus(status);
        v.setTriageLevel(triageLevel);
        return v;
    }

    @Test
    void registerVisit_unknownArrivalCreatesTemporaryPatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(visitRepository.countByHospitalId(1L)).thenReturn(4L);
        when(patientService.addPatient(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(visitRepository.save(any(EmergencyVisit.class))).thenAnswer(inv -> inv.getArgument(0));

        EmergencyVisitRequest req = new EmergencyVisitRequest();
        req.setUnknownPatient(true);
        req.setUnknownLabel("Unknown Male ~40");
        req.setArrivalMode("AMBULANCE");
        req.setIsMlc(true);

        EmergencyVisit visit = service.registerVisit(req);

        org.mockito.ArgumentCaptor<Patient> captor = org.mockito.ArgumentCaptor.forClass(Patient.class);
        verify(patientService).addPatient(captor.capture());
        assertThat(captor.getValue().getIsUnknown()).isTrue();
        assertThat(captor.getValue().getIsTemporary()).isTrue();
        assertThat(visit.getPatientId()).isEqualTo(99L);
        assertThat(visit.getEmergencyNumber()).isEqualTo("ER-5");
        assertThat(visit.getStatus()).isEqualTo("ACTIVE");
        assertThat(visit.getIsMlc()).isTrue();
    }

    @Test
    void registerVisit_rejectsCrossTenantPatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Patient foreign = new Patient();
        foreign.setId(7L);
        foreign.setHospitalId(99L);
        when(patientRepository.findById(7L)).thenReturn(Optional.of(foreign));

        EmergencyVisitRequest req = new EmergencyVisitRequest();
        req.setPatientId(7L);

        assertThatThrownBy(() -> service.registerVisit(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found under hospital tenant");
        verify(visitRepository, never()).save(any());
    }

    @Test
    void assess_rejectedBeforeTriage() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(visitRepository.findByIdAndHospitalId(5L, 1L))
                .thenReturn(Optional.of(openVisit(5L, 1L, "ACTIVE", null)));

        assertThatThrownBy(() -> service.assess(5L, new EmergencyVisitRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("triaged");
        verify(visitRepository, never()).save(any());
    }

    @Test
    void triage_validatesLevelAndStamps() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(visitRepository.findByIdAndHospitalId(5L, 1L))
                .thenReturn(Optional.of(openVisit(5L, 1L, "ACTIVE", null)));
        when(visitRepository.save(any(EmergencyVisit.class))).thenAnswer(inv -> inv.getArgument(0));

        EmergencyVisitRequest bad = new EmergencyVisitRequest();
        bad.setTriageLevel("PURPLE");
        assertThatThrownBy(() -> service.triage(5L, bad)).isInstanceOf(IllegalArgumentException.class);

        EmergencyVisitRequest req = new EmergencyVisitRequest();
        req.setTriageLevel("red");
        EmergencyVisit triaged = service.triage(5L, req);

        assertThat(triaged.getTriageLevel()).isEqualTo("RED");
        assertThat(triaged.getTriagedBy()).isEqualTo("nurse@hospital.com");
        assertThat(triaged.getTriagedAt()).isNotNull();
    }

    @Test
    void dispose_admitCallsAdmitFromEmergencyAndLinks() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        EmergencyVisit visit = openVisit(5L, 1L, "OBSERVATION", "RED");
        visit.setInitialDiagnosis("Polytrauma");
        when(visitRepository.findByIdAndHospitalId(5L, 1L)).thenReturn(Optional.of(visit));
        IpdAdmission admission = new IpdAdmission();
        admission.setId(300L);
        when(ipdAdmissionService.admitFromEmergency(eq(50L), eq(9L), eq(2L), eq(3L), eq("EMERGENCY"), anyString()))
                .thenReturn(admission);
        when(visitRepository.save(any(EmergencyVisit.class))).thenAnswer(inv -> inv.getArgument(0));

        EmergencyVisitRequest req = new EmergencyVisitRequest();
        req.setDisposition("ADMIT");
        req.setDoctorId(9L);
        req.setWardId(2L);
        req.setBedId(3L);
        EmergencyVisit disposed = service.dispose(5L, req);

        assertThat(disposed.getStatus()).isEqualTo("DISPOSED");
        assertThat(disposed.getIpdAdmissionId()).isEqualTo(300L);
    }

    @Test
    void dispose_admitRequiresBedAssignment() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(visitRepository.findByIdAndHospitalId(5L, 1L))
                .thenReturn(Optional.of(openVisit(5L, 1L, "OBSERVATION", "RED")));

        EmergencyVisitRequest req = new EmergencyVisitRequest();
        req.setDisposition("ICU"); // no doctor/ward/bed

        assertThatThrownBy(() -> service.dispose(5L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires");
        verify(ipdAdmissionService, never()).admitFromEmergency(any(), any(), any(), any(), any(), any());
    }

    @Test
    void disposedVisit_isReadOnly() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(visitRepository.findByIdAndHospitalId(5L, 1L))
                .thenReturn(Optional.of(openVisit(5L, 1L, "DISPOSED", "GREEN")));

        assertThatThrownBy(() -> service.triage(5L, new EmergencyVisitRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void getActiveVisits_requiresTenantContext() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(null);
        assertThatThrownBy(() -> service.getActiveVisits())
                .isInstanceOf(UnauthorizedException.class);
    }
}
