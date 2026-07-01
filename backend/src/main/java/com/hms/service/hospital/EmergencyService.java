package com.hms.service.hospital;

import com.hms.dto.EmergencyVisitRequest;
import com.hms.entity.AuditLog;
import com.hms.entity.EmergencyVisit;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Patient;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.EmergencyVisitRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emergency Information System (Form 12 core). Treatment-first flow:
 * unknown arrivals get a temporary tenant-scoped Patient immediately (BR-2/BR-3),
 * triage is mandatory before assessment/disposition (BR-1), and an ADMIT/ICU
 * disposition initiates the admission via admitFromEmergency (BR-4).
 */
@Service
public class EmergencyService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);

    private static final List<String> TRIAGE_LEVELS = List.of("RED", "ORANGE", "YELLOW", "GREEN", "BLACK");
    private static final List<String> DISPOSITIONS = List.of("ADMIT", "ICU", "OT", "DISCHARGE", "REFER", "DEATH");

    @Autowired
    private EmergencyVisitRepository visitRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PatientService patientService;

    @Autowired
    private IpdAdmissionService ipdAdmissionService;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    /** Priority board: active + observation visits, tenant-scoped. */
    public List<EmergencyVisit> getActiveVisits() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return visitRepository.findByHospitalIdAndStatusInOrderByArrivalTimeDesc(
                hospitalId, List.of("ACTIVE", "OBSERVATION"));
    }

    /** BR-2/BR-3: register an ER arrival; unknown patients get an immediate temporary identity. */
    @Transactional
    public EmergencyVisit registerVisit(EmergencyVisitRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        Long patientId;
        if (Boolean.TRUE.equals(request.getUnknownPatient())) {
            Patient temp = new Patient();
            temp.setName(request.getUnknownLabel() != null && !request.getUnknownLabel().isBlank()
                    ? request.getUnknownLabel()
                    : "Unknown Patient");
            temp.setAge(request.getApproximateAge() != null ? request.getApproximateAge() : 0);
            temp.setGender(request.getGender() != null && !request.getGender().isBlank()
                    ? request.getGender() : "OTHER");
            temp.setPhone("0000000000");
            temp.setIsTemporary(true);
            temp.setIsUnknown(true);
            Patient saved = patientService.addPatient(temp);
            patientId = saved.getId();
        } else {
            if (request.getPatientId() == null) {
                throw new IllegalArgumentException("Either patientId or unknownPatient=true is required");
            }
            Patient patient = patientRepository.findById(request.getPatientId())
                    .filter(p -> p.getHospitalId().equals(hospitalId))
                    .orElseThrow(() -> new RuntimeException("Patient not found under hospital tenant"));
            patientId = patient.getId();
        }

        EmergencyVisit visit = new EmergencyVisit();
        visit.setHospitalId(hospitalId);
        visit.setPatientId(patientId);
        visit.setEmergencyNumber("ER-" + (visitRepository.countByHospitalId(hospitalId) + 1));
        visit.setArrivalTime(LocalDateTime.now());
        visit.setArrivalMode(request.getArrivalMode());
        visit.setIsMlc(Boolean.TRUE.equals(request.getIsMlc()));
        visit.setMlcNumber(request.getMlcNumber());
        visit.setStatus("ACTIVE");
        EmergencyVisit saved = visitRepository.save(visit);

        audit("ER_VISIT_REGISTERED", "Emergency visit " + saved.getEmergencyNumber() + " registered"
                + (Boolean.TRUE.equals(request.getUnknownPatient()) ? " (unknown/temporary patient)" : "")
                + (Boolean.TRUE.equals(request.getIsMlc()) ? " [MLC]" : ""), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-1: triage. Recordable/re-recordable while the visit is open. */
    @Transactional
    public EmergencyVisit triage(Long visitId, EmergencyVisitRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        EmergencyVisit visit = loadOpenVisit(visitId, hospitalId);

        String level = request.getTriageLevel() == null ? "" : request.getTriageLevel().toUpperCase();
        if (!TRIAGE_LEVELS.contains(level)) {
            throw new IllegalArgumentException("Invalid triage level: " + request.getTriageLevel());
        }
        visit.setTriageLevel(level);
        visit.setTriageCriteria(request.getTriageCriteria());
        visit.setTriagedBy(securityHelper.getCurrentUserEmail());
        visit.setTriagedAt(LocalDateTime.now());
        EmergencyVisit saved = visitRepository.save(visit);

        audit("ER_TRIAGED", "Emergency visit " + visit.getEmergencyNumber() + " triaged " + level, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Primary ABC/GCS assessment. BR-1: requires triage first. */
    @Transactional
    public EmergencyVisit assess(Long visitId, EmergencyVisitRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        EmergencyVisit visit = loadOpenVisit(visitId, hospitalId);

        if (visit.getTriageLevel() == null) {
            throw new IllegalStateException("Cannot record the assessment before the patient is triaged (BR-1).");
        }
        if (request.getGcsScore() != null && (request.getGcsScore() < 3 || request.getGcsScore() > 15)) {
            throw new IllegalArgumentException("GCS score must be between 3 and 15.");
        }
        visit.setChiefComplaint(request.getChiefComplaint());
        visit.setAirwayStatus(request.getAirwayStatus());
        visit.setBreathingStatus(request.getBreathingStatus());
        visit.setCirculationStatus(request.getCirculationStatus());
        visit.setGcsScore(request.getGcsScore());
        visit.setInitialDiagnosis(request.getInitialDiagnosis());
        visit.setAssessedBy(securityHelper.getCurrentUserEmail());
        visit.setAssessedAt(LocalDateTime.now());
        if ("ACTIVE".equalsIgnoreCase(visit.getStatus())) {
            visit.setStatus("OBSERVATION");
        }
        EmergencyVisit saved = visitRepository.save(visit);

        audit("ER_ASSESSED", "Emergency visit " + visit.getEmergencyNumber() + " assessed", hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-4: disposition; ADMIT/ICU initiates an IPD admission via admitFromEmergency. */
    @Transactional
    public EmergencyVisit dispose(Long visitId, EmergencyVisitRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        EmergencyVisit visit = loadOpenVisit(visitId, hospitalId);

        if (visit.getTriageLevel() == null) {
            throw new IllegalStateException("Cannot dispose before the patient is triaged (BR-1).");
        }
        String disposition = request.getDisposition() == null ? "" : request.getDisposition().toUpperCase();
        if (!DISPOSITIONS.contains(disposition)) {
            throw new IllegalArgumentException("Invalid disposition: " + request.getDisposition());
        }

        if ("ADMIT".equals(disposition) || "ICU".equals(disposition)) {
            if (request.getDoctorId() == null || request.getWardId() == null || request.getBedId() == null) {
                throw new IllegalStateException("Admission requires doctorId, wardId and bedId.");
            }
            IpdAdmission admission = ipdAdmissionService.admitFromEmergency(
                    visit.getPatientId(), request.getDoctorId(), request.getWardId(), request.getBedId(),
                    "EMERGENCY", visit.getInitialDiagnosis());
            visit.setIpdAdmissionId(admission.getId());
        }

        visit.setDisposition(disposition);
        visit.setStatus("DISPOSED");
        EmergencyVisit saved = visitRepository.save(visit);

        audit("ER_DISPOSED", "Emergency visit " + visit.getEmergencyNumber() + " disposed: " + disposition, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private EmergencyVisit loadOpenVisit(Long visitId, Long hospitalId) {
        EmergencyVisit visit = visitRepository.findByIdAndHospitalId(visitId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Emergency visit not found: " + visitId));
        if ("DISPOSED".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalStateException("This emergency visit has been disposed and is read-only.");
        }
        return visit;
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
