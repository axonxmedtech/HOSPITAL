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

        DoctorRound saved = roundRepository.save(round);

        audit("DOCTOR_ROUND_RECORDED", "Doctor round recorded for IPD " + ipdAdmissionId + " by Dr. " + doctor.getName(), hospitalId);
        broadcast(hospitalId);

        return saved;
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
