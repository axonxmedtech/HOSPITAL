package com.hms.service;

import com.hms.dto.DoctorRoundRequest;
import com.hms.entity.Doctor;
import com.hms.entity.DoctorRound;
import com.hms.entity.IpdAdmission;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.DoctorRoundRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.DoctorRoundService;
import com.hms.security.HospitalWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorRoundServiceTest {

    @Mock
    private DoctorRoundRepository roundRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private HospitalWebSocketHandler webSocketHandler;

    @Mock
    private com.hms.service.hospital.MrdService mrdService;

    @InjectMocks
    private DoctorRoundService doctorRoundService;

    @Test
    void logRound_savesSoapNotesSuccessfully() {
        Long hospitalId = 1L;
        Long admissionId = 10L;

        // Mock Security Context for Actor Auditing
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("doctor@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doctor@hospital.com");

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        Doctor doctor = new Doctor();
        doctor.setId(5L);
        doctor.setName("Dr. John Smith");
        when(doctorRepository.findByEmailAndHospitalId("doctor@hospital.com", hospitalId))
                .thenReturn(Optional.of(doctor));

        when(roundRepository.save(any(DoctorRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DoctorRoundRequest req = new DoctorRoundRequest(
                "Patient feels tired", "Chest clear", "Infection resolving", "Continue current medications",
                LocalDateTime.now().plusDays(1)
        );

        DoctorRound round = doctorRoundService.logRound(admissionId, req);

        assertThat(round.getSubjective()).isEqualTo("Patient feels tired");
        assertThat(round.getObjective()).isEqualTo("Chest clear");
        assertThat(round.getAssessment()).isEqualTo("Infection resolving");
        assertThat(round.getPlan()).isEqualTo("Continue current medications");
        assertThat(round.getDoctorName()).isEqualTo("Dr. John Smith");
        assertThat(round.getHospitalId()).isEqualTo(hospitalId);
        assertThat(round.getIpdAdmissionId()).isEqualTo(admissionId);

        verify(roundRepository, times(1)).save(any(DoctorRound.class));
        verify(webSocketHandler, times(1)).broadcast(eq(hospitalId), anyString());
    }

    @Test
    void logRound_throwsTenantMismatch() {
        Long hospitalId = 1L;
        Long admissionId = 10L;

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(99L); // Mismatched hospitalId
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        DoctorRoundRequest req = new DoctorRoundRequest("S", "O", "A", "P", null);

        assertThatThrownBy(() -> doctorRoundService.logRound(admissionId, req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Access denied: Tenant mismatch");
    }
}
