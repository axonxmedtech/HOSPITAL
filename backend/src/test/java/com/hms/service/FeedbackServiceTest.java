package com.hms.service;

import com.hms.dto.FeedbackTokenIssueRequest;
import com.hms.dto.PatientFeedbackSubmitRequest;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.FeedbackService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackTokenRepository tokenRepository;
    @Mock private PatientFeedbackRepository feedbackRepository;
    @Mock private QualityComplaintRepository complaintRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private FeedbackService service;

    // ===== BR-1: token only for completed encounters =====

    @Test
    void issueToken_opd_rejectedWhenAppointmentNotCompleted() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        Appointment appt = new Appointment();
        appt.setId(5L);
        appt.setHospitalId(hospitalId);
        appt.setStatus("SCHEDULED");
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appt));

        FeedbackTokenIssueRequest req = new FeedbackTokenIssueRequest();
        req.setFeedbackType("OPD");
        req.setPatientId(50L);
        req.setAppointmentId(5L);

        assertThatThrownBy(() -> service.issueToken(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not completed");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void issueToken_ipd_succeedsWhenDischarged() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("reception@hospital.com");
        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(20L);
        ipd.setHospitalId(hospitalId);
        ipd.setStatus("DISCHARGED");
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(ipd));
        when(tokenRepository.save(any(FeedbackToken.class))).thenAnswer(i -> i.getArgument(0));

        FeedbackTokenIssueRequest req = new FeedbackTokenIssueRequest();
        req.setFeedbackType("IPD");
        req.setPatientId(50L);
        req.setAdmissionId(20L);

        FeedbackToken saved = service.issueToken(req);

        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getFeedbackType()).isEqualTo("IPD");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ===== BR-2: one per encounter =====

    @Test
    void issueToken_rejectedWhenAlreadyIssuedForEncounter() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(20L);
        ipd.setHospitalId(hospitalId);
        ipd.setStatus("DISCHARGED");
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(ipd));
        when(tokenRepository.existsByHospitalIdAndAdmissionId(hospitalId, 20L)).thenReturn(true);

        FeedbackTokenIssueRequest req = new FeedbackTokenIssueRequest();
        req.setFeedbackType("IPD");
        req.setPatientId(50L);
        req.setAdmissionId(20L);

        assertThatThrownBy(() -> service.issueToken(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    // ===== BR-3: expired/used token blocked =====

    @Test
    void submitFeedback_rejectedWhenTokenAlreadyUsed() {
        FeedbackToken token = new FeedbackToken();
        token.setToken("tok-1");
        token.setUsedAt(LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(5);

        assertThatThrownBy(() -> service.submitFeedback("tok-1", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been used");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitFeedback_rejectedWhenTokenExpired() {
        FeedbackToken token = new FeedbackToken();
        token.setToken("tok-1");
        token.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(5);

        assertThatThrownBy(() -> service.submitFeedback("tok-1", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    // ===== BR-7: patient/hospital resolved server-side from token =====

    @Test
    void submitFeedback_resolvesPatientAndHospitalFromToken() {
        FeedbackToken token = new FeedbackToken();
        token.setId(1L);
        token.setToken("tok-1");
        token.setHospitalId(1L);
        token.setPatientId(77L);
        token.setFeedbackType("OPD");
        token.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));
        when(feedbackRepository.save(any(PatientFeedback.class))).thenAnswer(i -> {
            PatientFeedback f = i.getArgument(0);
            f.setId(99L);
            return f;
        });
        when(tokenRepository.save(any(FeedbackToken.class))).thenAnswer(i -> i.getArgument(0));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(5);
        req.setDoctorRating(5);

        PatientFeedback saved = service.submitFeedback("tok-1", req);

        assertThat(saved.getHospitalId()).isEqualTo(1L);
        assertThat(saved.getPatientId()).isEqualTo(77L);
        assertThat(token.getUsedAt()).isNotNull(); // single-use marked
        verifyNoInteractions(complaintRepository); // no low rating, no complaint
    }

    // ===== BR-4: auto quality complaint on low rating / complaint text =====

    @Test
    void submitFeedback_lowRating_autoCreatesComplaint() {
        FeedbackToken token = new FeedbackToken();
        token.setToken("tok-1");
        token.setHospitalId(1L);
        token.setPatientId(77L);
        token.setFeedbackType("IPD");
        token.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));
        when(feedbackRepository.save(any(PatientFeedback.class))).thenAnswer(i -> {
            PatientFeedback f = i.getArgument(0);
            f.setId(99L);
            return f;
        });
        when(complaintRepository.save(any(QualityComplaint.class))).thenAnswer(i -> i.getArgument(0));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(4);
        req.setDoctorRating(1); // low

        service.submitFeedback("tok-1", req);

        org.mockito.ArgumentCaptor<QualityComplaint> captor = org.mockito.ArgumentCaptor.forClass(QualityComplaint.class);
        verify(complaintRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("DOCTOR");
        assertThat(captor.getValue().getSeverity()).isEqualTo("MEDIUM");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void submitFeedback_complaintText_autoCreatesComplaintEvenWithGoodRatings() {
        FeedbackToken token = new FeedbackToken();
        token.setToken("tok-1");
        token.setHospitalId(1L);
        token.setPatientId(77L);
        token.setFeedbackType("OPD");
        token.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));
        when(feedbackRepository.save(any(PatientFeedback.class))).thenAnswer(i -> {
            PatientFeedback f = i.getArgument(0);
            f.setId(99L);
            return f;
        });
        when(complaintRepository.save(any(QualityComplaint.class))).thenAnswer(i -> i.getArgument(0));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(5);
        req.setComplaints("Waiting room AC was not working");

        service.submitFeedback("tok-1", req);

        verify(complaintRepository, times(1)).save(any(QualityComplaint.class));
    }

    @Test
    void submitFeedback_invalidRatingRange_rejected() {
        FeedbackToken token = new FeedbackToken();
        token.setToken("tok-1");
        token.setExpiresAt(LocalDateTime.now().plusDays(3));
        when(tokenRepository.findByToken("tok-1")).thenReturn(Optional.of(token));

        PatientFeedbackSubmitRequest req = new PatientFeedbackSubmitRequest();
        req.setOverallRating(9); // out of 1-5 range

        assertThatThrownBy(() -> service.submitFeedback("tok-1", req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(feedbackRepository, never()).save(any());
    }

    // ===== Admin resolution =====

    @Test
    void resolveComplaint_requiresResolutionNote() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        assertThatThrownBy(() -> service.resolveComplaint(1L, ""))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(complaintRepository);
    }

    @Test
    void resolveComplaint_closesWithResolution() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");
        QualityComplaint complaint = new QualityComplaint();
        complaint.setId(1L);
        complaint.setStatus("OPEN");
        when(complaintRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(QualityComplaint.class))).thenAnswer(i -> i.getArgument(0));

        QualityComplaint resolved = service.resolveComplaint(1L, "AC repaired same day");

        assertThat(resolved.getStatus()).isEqualTo("CLOSED");
        assertThat(resolved.getResolution()).isEqualTo("AC repaired same day");
    }
}
