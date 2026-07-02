package com.hms.service.hospital;

import com.hms.dto.FeedbackTokenIssueRequest;
import com.hms.dto.PatientFeedbackSubmitRequest;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Patient Feedback (Form 03 core). Token-based public submission (BR-7: the public payload
 * never carries hospital_id/patient_id — everything resolves server-side from the token),
 * immutable feedback (BR-5), and auto-generated quality complaints on low ratings (BR-4).
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final int TOKEN_VALIDITY_DAYS = 7;
    private static final Set<String> COMPLETED_OPD_STATUSES = Set.of("COMPLETED");
    private static final Set<String> COMPLETED_IPD_STATUSES = Set.of("DISCHARGED");

    @Autowired private FeedbackTokenRepository tokenRepository;
    @Autowired private PatientFeedbackRepository feedbackRepository;
    @Autowired private QualityComplaintRepository complaintRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    /** BR-1: issues a single-use token only for a completed encounter; BR-2 (one per encounter). */
    @Transactional
    public FeedbackToken issueToken(FeedbackTokenIssueRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String type = request.getFeedbackType() == null ? "" : request.getFeedbackType().toUpperCase();
        if (!type.equals("OPD") && !type.equals("IPD")) {
            throw new IllegalArgumentException("feedbackType must be OPD or IPD");
        }

        if (type.equals("OPD")) {
            if (request.getAppointmentId() == null) throw new IllegalArgumentException("appointmentId is required for OPD feedback");
            Appointment appt = appointmentRepository.findById(request.getAppointmentId())
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));
            if (!appt.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied: Tenant mismatch");
            if (!COMPLETED_OPD_STATUSES.contains(String.valueOf(appt.getStatus()).toUpperCase())) {
                throw new IllegalStateException("Cannot issue feedback token: appointment is not completed (BR-1).");
            }
            if (tokenRepository.existsByHospitalIdAndAppointmentId(hospitalId, request.getAppointmentId())
                    || feedbackRepository.existsByHospitalIdAndAppointmentId(hospitalId, request.getAppointmentId())) {
                throw new IllegalStateException("A feedback token/response already exists for this encounter (BR-2).");
            }
        } else {
            if (request.getAdmissionId() == null) throw new IllegalArgumentException("admissionId is required for IPD feedback");
            IpdAdmission ipd = ipdAdmissionRepository.findById(request.getAdmissionId())
                    .orElseThrow(() -> new RuntimeException("Admission not found"));
            if (!ipd.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied: Tenant mismatch");
            if (!COMPLETED_IPD_STATUSES.contains(String.valueOf(ipd.getStatus()).toUpperCase())) {
                throw new IllegalStateException("Cannot issue feedback token: admission is not discharged (BR-1).");
            }
            if (tokenRepository.existsByHospitalIdAndAdmissionId(hospitalId, request.getAdmissionId())
                    || feedbackRepository.existsByHospitalIdAndAdmissionId(hospitalId, request.getAdmissionId())) {
                throw new IllegalStateException("A feedback token/response already exists for this encounter (BR-2).");
            }
        }

        FeedbackToken token = new FeedbackToken();
        token.setToken(UUID.randomUUID().toString());
        token.setHospitalId(hospitalId);
        token.setPatientId(request.getPatientId());
        token.setAppointmentId(request.getAppointmentId());
        token.setAdmissionId(request.getAdmissionId());
        token.setFeedbackType(type);
        token.setExpiresAt(LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS));
        FeedbackToken saved = tokenRepository.save(token);

        audit("FEEDBACK_TOKEN_ISSUED", "Feedback token issued for "
                + type + " encounter, patient " + request.getPatientId(), hospitalId, securityHelper.getCurrentUserEmail());
        return saved;
    }

    /**
     * BR-3/BR-7: public submission — resolves patient/hospital from the token server-side;
     * the request payload itself never carries tenant/patient identifiers.
     */
    @Transactional
    public PatientFeedback submitFeedback(String tokenValue, PatientFeedbackSubmitRequest request) {
        FeedbackToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid feedback link."));
        if (token.getUsedAt() != null) {
            throw new IllegalStateException("This feedback link has already been used.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This feedback link has expired.");
        }
        if (request.getOverallRating() == null || request.getOverallRating() < 1 || request.getOverallRating() > 5) {
            throw new IllegalArgumentException("Overall rating (1-5) is required.");
        }
        validateRating(request.getReceptionRating(), "reception");
        validateRating(request.getDoctorRating(), "doctor");
        validateRating(request.getNurseRating(), "nurse");
        validateRating(request.getHousekeepingRating(), "housekeeping");
        validateRating(request.getBillingRating(), "billing");
        if (request.getRecommendScore() != null && (request.getRecommendScore() < 0 || request.getRecommendScore() > 10)) {
            throw new IllegalArgumentException("Recommend score must be 0-10.");
        }

        PatientFeedback feedback = new PatientFeedback();
        feedback.setHospitalId(token.getHospitalId());
        feedback.setPatientId(token.getPatientId());
        feedback.setAppointmentId(token.getAppointmentId());
        feedback.setAdmissionId(token.getAdmissionId());
        feedback.setFeedbackType(token.getFeedbackType());
        feedback.setSubmittedBy(request.getSubmittedBy() != null ? request.getSubmittedBy() : "PATIENT");
        feedback.setSource(request.getSource());
        feedback.setOverallRating(request.getOverallRating());
        feedback.setReceptionRating(request.getReceptionRating());
        feedback.setDoctorRating(request.getDoctorRating());
        feedback.setNurseRating(request.getNurseRating());
        feedback.setHousekeepingRating(request.getHousekeepingRating());
        feedback.setBillingRating(request.getBillingRating());
        feedback.setFacilityRating(request.getFacilityRating());
        feedback.setRecommendScore(request.getRecommendScore());
        feedback.setComplaints(request.getComplaints());
        feedback.setSuggestions(request.getSuggestions());
        feedback.setStatus("SUBMITTED");
        feedback.setSubmittedAt(LocalDateTime.now());
        PatientFeedback saved = feedbackRepository.save(feedback);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // BR-4: auto-create a quality complaint on any low rating or free-text complaint.
        boolean lowRating = hasLowRating(saved);
        boolean hasComplaintText = saved.getComplaints() != null && !saved.getComplaints().isBlank();
        if (lowRating || hasComplaintText) {
            QualityComplaint complaint = new QualityComplaint();
            complaint.setHospitalId(saved.getHospitalId());
            complaint.setFeedbackId(saved.getId());
            complaint.setCategory(inferCategory(saved));
            complaint.setDescription(hasComplaintText ? saved.getComplaints() : "Low satisfaction rating(s) reported.");
            complaint.setSeverity(saved.getOverallRating() <= 1 ? "HIGH" : (lowRating ? "MEDIUM" : "LOW"));
            complaint.setStatus("OPEN");
            complaintRepository.save(complaint);
        }

        audit("FEEDBACK_SUBMITTED", "Feedback submitted for "
                + saved.getFeedbackType() + " encounter (overall: " + saved.getOverallRating() + ")", saved.getHospitalId(), "public-token");
        return saved;
    }

    /** BR-6: quality/admin-only read (enforced primarily via controller @PreAuthorize). */
    public List<PatientFeedback> getFeedback() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return feedbackRepository.findByHospitalIdOrderBySubmittedAtDesc(hospitalId);
    }

    public List<QualityComplaint> getComplaints() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return complaintRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
    }

    @Transactional
    public QualityComplaint resolveComplaint(Long complaintId, String resolution) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("A resolution note is required.");
        }
        QualityComplaint complaint = complaintRepository.findByIdAndHospitalId(complaintId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        complaint.setStatus("CLOSED");
        complaint.setResolution(resolution);
        complaint.setResolvedByName(securityHelper.getCurrentUserEmail());
        complaint.setResolvedAt(LocalDateTime.now());
        return complaintRepository.save(complaint);
    }

    private void validateRating(Integer rating, String label) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("The " + label + " rating must be 1-5.");
        }
    }

    private boolean hasLowRating(PatientFeedback f) {
        return isLow(f.getOverallRating()) || isLow(f.getReceptionRating()) || isLow(f.getDoctorRating())
                || isLow(f.getNurseRating()) || isLow(f.getHousekeepingRating()) || isLow(f.getBillingRating());
    }

    private boolean isLow(Integer rating) {
        return rating != null && rating <= 2;
    }

    private String inferCategory(PatientFeedback f) {
        if (isLow(f.getDoctorRating())) return "DOCTOR";
        if (isLow(f.getNurseRating())) return "NURSING";
        if (isLow(f.getReceptionRating())) return "RECEPTION";
        if (isLow(f.getHousekeepingRating())) return "CLEANLINESS";
        if (isLow(f.getBillingRating())) return "BILLING";
        return "OTHER";
    }

    private void audit(String action, String details, Long hospitalId, String actor) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(actor != null ? actor : "system");
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
