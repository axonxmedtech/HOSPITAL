package com.hms.service.hospital;

import com.hms.dto.DoctorRoundRequest;
import com.hms.entity.AuditLog;
import com.hms.entity.Doctor;
import com.hms.entity.DoctorRound;
import com.hms.entity.IpdAdmission;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.DoctorRoundRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DoctorRoundService {

    private static final Logger log = LoggerFactory.getLogger(DoctorRoundService.class);

    @Autowired
    private DoctorRoundRepository roundRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MrdService mrdService;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    public List<DoctorRound> getRoundsHistory(Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + ipdAdmissionId));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        return roundRepository.findByIpdAdmissionIdAndHospitalIdOrderByRoundDateTimeDesc(ipdAdmissionId, hospitalId);
    }

    @Transactional
    public DoctorRound logRound(Long ipdAdmissionId, DoctorRoundRequest request) {
        mrdService.validateAdmissionActive(ipdAdmissionId);
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String email = securityHelper.getCurrentUserEmail();

        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + ipdAdmissionId));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                .orElseThrow(() -> new UnauthorizedException("Doctor profile not found for this user"));

        DoctorRound round = new DoctorRound();
        round.setIpdAdmissionId(ipdAdmissionId);
        round.setHospitalId(hospitalId);
        round.setDoctorId(doctor.getId());
        round.setDoctorName(doctor.getName());
        round.setRoundDateTime(LocalDateTime.now());
        round.setSubjective(request.getSubjective());
        round.setObjective(request.getObjective());
        round.setAssessment(request.getAssessment());
        round.setPlan(request.getPlan());
        round.setNextRoundAt(request.getNextRoundAt());
        // Clinical Documentation Engine (Forms 11/13): notes are signed at creation.
        round.setAssessmentType(normalizeAssessmentType(request.getAssessmentType()));
        round.setStatus("SIGNED");
        round.setSignedBy(email);
        round.setSignedAt(LocalDateTime.now());

        DoctorRound saved = roundRepository.save(round);

        audit("DOCTOR_ROUND_RECORDED", "Doctor round (" + saved.getAssessmentType() + ") recorded for IPD "
                + ipdAdmissionId + " by Dr. " + doctor.getName(), hospitalId);
        broadcast(hospitalId);

        return saved;
    }

    /**
     * Amends a signed note (Forms 11/13 medico-legal lifecycle): the original is NEVER mutated —
     * it is marked AMENDED and a new signed note is created linked via amended_from_id.
     */
    @Transactional
    public DoctorRound amendRound(Long roundId, DoctorRoundRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        DoctorRound original = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Doctor round not found: " + roundId));
        if (!original.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(original.getIpdAdmissionId());
        if ("AMENDED".equalsIgnoreCase(original.getStatus())) {
            throw new IllegalStateException("This note has already been amended; amend the latest version instead.");
        }
        if (request.getAmendmentReason() == null || request.getAmendmentReason().isBlank()) {
            throw new IllegalStateException("An amendment reason is required.");
        }

        Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                .orElseThrow(() -> new UnauthorizedException("Doctor profile not found for this user"));

        DoctorRound amendment = new DoctorRound();
        amendment.setIpdAdmissionId(original.getIpdAdmissionId());
        amendment.setHospitalId(hospitalId);
        amendment.setDoctorId(doctor.getId());
        amendment.setDoctorName(doctor.getName());
        amendment.setRoundDateTime(LocalDateTime.now());
        amendment.setSubjective(request.getSubjective());
        amendment.setObjective(request.getObjective());
        amendment.setAssessment(request.getAssessment());
        amendment.setPlan(request.getPlan());
        amendment.setNextRoundAt(request.getNextRoundAt());
        amendment.setAssessmentType(original.getAssessmentType() != null
                ? original.getAssessmentType()
                : normalizeAssessmentType(request.getAssessmentType()));
        amendment.setStatus("SIGNED");
        amendment.setSignedBy(email);
        amendment.setSignedAt(LocalDateTime.now());
        amendment.setAmendedFromId(original.getId());
        amendment.setAmendmentReason(request.getAmendmentReason());
        DoctorRound saved = roundRepository.save(amendment);

        // Mark the original superseded — content untouched (immutable medico-legal record).
        original.setStatus("AMENDED");
        roundRepository.save(original);

        audit("DOCTOR_ROUND_AMENDED", "Doctor round " + roundId + " amended (new note " + saved.getId()
                + ") by Dr. " + doctor.getName() + ": " + request.getAmendmentReason(), hospitalId);
        broadcast(hospitalId);

        return saved;
    }

    private String normalizeAssessmentType(String type) {
        if (type == null || type.isBlank()) return "PROGRESS_NOTE";
        String t = type.toUpperCase();
        if (t.equals("PROGRESS_NOTE") || t.equals("REASSESSMENT")) return t;
        throw new IllegalArgumentException("Invalid assessment type: " + type);
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(actor);
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private void broadcast(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
