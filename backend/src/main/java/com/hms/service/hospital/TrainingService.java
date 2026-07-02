package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * HR Training / Learning Management (Form 04 core). Implements the NABH competency-evidence
 * chain: course (master) -> session -> attendance marking -> trainer verification (BR-5:
 * completion = PRESENT + verified) -> certification with expiry sweep (BR-6). BR-7: verified
 * attendance is only changed via an audited HR correction with a reason. BR-9: tenant-guarded.
 *
 * Staff identity: uses the canonical {@link com.hms.entity.Employee} record introduced in
 * Form 39, resolving the identity gap this form's blueprint flags as a prerequisite.
 *
 * Scope note: the role->training auto-assignment matrix (BR-1) and department/role compliance
 * percentage reporting (BR-8) are deferred — this covers the auditable record-and-certify
 * chain a NABH inspector actually checks.
 */
@Service
public class TrainingService {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

    private static final Set<String> VALID_ATTENDANCE_STATUSES = Set.of("PRESENT", "ABSENT", "LATE");
    private static final int EXPIRING_WINDOW_DAYS = 30;

    @Autowired private TrainingMasterRepository masterRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private TrainingAttendanceRepository attendanceRepository;
    @Autowired private TrainingCertificationRepository certificationRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional
    public TrainingMaster createMaster(TrainingMasterRequest request) {
        Long hospitalId = requireHospital();
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        int validity = request.getValidityPeriod() == null ? 0 : request.getValidityPeriod();
        if (validity < 0) {
            throw new IllegalArgumentException("Validity period cannot be negative");
        }

        TrainingMaster master = new TrainingMaster();
        master.setHospitalId(hospitalId);
        master.setTitle(request.getTitle());
        master.setCategory(request.getCategory().toUpperCase());
        master.setDescription(request.getDescription());
        master.setMandatory(Boolean.TRUE.equals(request.getMandatory()));
        master.setValidityPeriod(validity);
        master.setTargetRoles(request.getTargetRoles());
        TrainingMaster saved = masterRepository.save(master);
        audit("TRAINING_MASTER_CREATED", "Course \"" + saved.getTitle() + "\" created (" + saved.getCategory() + ")", hospitalId);
        return saved;
    }

    public List<TrainingMaster> getMasters() {
        return masterRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    @Transactional
    public TrainingSession createSession(TrainingSessionRequest request) {
        Long hospitalId = requireHospital();
        TrainingMaster master = masterRepository.findByIdAndHospitalId(request.getTrainingMasterId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Training course not found"));
        if (request.getTrainerId() == null) {
            throw new IllegalArgumentException("Trainer is required");
        }
        if (request.getSessionDate() == null) {
            throw new IllegalArgumentException("Session date is required");
        }
        if (request.getSessionDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Session date cannot be in the past");
        }
        if (request.getStartTime() == null || request.getEndTime() == null || !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (request.getVenue() == null || request.getVenue().isBlank()) {
            throw new IllegalArgumentException("Venue is required");
        }

        TrainingSession session = new TrainingSession();
        session.setHospitalId(hospitalId);
        session.setTrainingMasterId(master.getId());
        session.setTrainerId(request.getTrainerId());
        session.setSessionDate(request.getSessionDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setVenue(request.getVenue());
        session.setStatus("PLANNED");
        TrainingSession saved = sessionRepository.save(session);
        audit("TRAINING_SESSION_CREATED", "Session for \"" + master.getTitle() + "\" on " + saved.getSessionDate() + " at " + saved.getVenue(), hospitalId);
        return saved;
    }

    public List<TrainingSession> getSessions() {
        return sessionRepository.findByHospitalIdOrderBySessionDateDesc(requireHospital());
    }

    @Transactional
    public TrainingSession cancelSession(Long sessionId, SessionCancelRequest request) {
        Long hospitalId = requireHospital();
        TrainingSession session = sessionRepository.findByIdAndHospitalId(sessionId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new IllegalStateException("Cannot cancel a session that is already " + session.getStatus());
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }
        session.setStatus("CANCELLED");
        session.setCancelReason(request.getReason());
        TrainingSession saved = sessionRepository.save(session);
        audit("TRAINING_SESSION_CANCELLED", "Session #" + session.getId() + " cancelled: " + request.getReason(), hospitalId);
        return saved;
    }

    /**
     * BR-2: employee must belong to this hospital. BR-3: check-out must follow check-in.
     * BR-4: a PLANNED session is auto-started by its first attendance mark; marking against an
     * already COMPLETED/CANCELLED session requires a remark (post-hoc correction).
     */
    @Transactional
    public TrainingAttendance markAttendance(TrainingAttendanceMarkRequest request) {
        Long hospitalId = requireHospital();
        TrainingSession session = sessionRepository.findByIdAndHospitalId(request.getSessionId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        Employee employee = employeeRepository.findByIdAndHospitalId(request.getEmployeeId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Employee not found (BR-2)"));

        if (attendanceRepository.findByHospitalIdAndSessionIdAndEmployeeId(hospitalId, session.getId(), employee.getId()).isPresent()) {
            throw new IllegalStateException("Attendance has already been recorded for this employee in this session");
        }
        String status = request.getAttendanceStatus() == null ? "" : request.getAttendanceStatus().toUpperCase();
        if (!VALID_ATTENDANCE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Attendance status must be one of " + VALID_ATTENDANCE_STATUSES);
        }
        if (request.getCheckInTime() != null && request.getCheckOutTime() != null
                && !request.getCheckOutTime().isAfter(request.getCheckInTime())) {
            throw new IllegalArgumentException("Check-out time must be after check-in time");
        }

        if ("COMPLETED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            if (request.getRemarks() == null || request.getRemarks().isBlank()) {
                throw new IllegalArgumentException("A remark is required to mark attendance on a " + session.getStatus() + " session (BR-4)");
            }
        } else if ("PLANNED".equals(session.getStatus())) {
            session.setStatus("IN_PROGRESS");
            sessionRepository.save(session);
        }

        TrainingAttendance attendance = new TrainingAttendance();
        attendance.setHospitalId(hospitalId);
        attendance.setSessionId(session.getId());
        attendance.setEmployeeId(employee.getId());
        attendance.setDepartment(request.getDepartment() != null ? request.getDepartment() : employee.getDepartment());
        attendance.setAttendanceStatus(status);
        attendance.setCheckInTime(request.getCheckInTime());
        attendance.setCheckOutTime(request.getCheckOutTime());
        attendance.setRemarks(request.getRemarks());
        TrainingAttendance saved = attendanceRepository.save(attendance);
        audit("TRAINING_ATTENDANCE_MARKED", employee.getEmployeeCode() + " marked " + status + " for session #" + session.getId(), hospitalId);
        return saved;
    }

    public List<TrainingAttendance> getAttendance() {
        return attendanceRepository.findByHospitalIdOrderByIdDesc(requireHospital());
    }

    /**
     * BR-5: completion = PRESENT + trainer verification. Verifying the session locks each
     * PRESENT attendance row and issues/renews the certification (BR-6 expiry) for it.
     */
    @Transactional
    public TrainingSession verifySession(Long sessionId) {
        Long hospitalId = requireHospital();
        TrainingSession session = sessionRepository.findByIdAndHospitalId(sessionId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if ("CANCELLED".equals(session.getStatus())) {
            throw new IllegalStateException("Cannot verify a cancelled session");
        }
        if ("COMPLETED".equals(session.getStatus())) {
            throw new IllegalStateException("Session has already been verified and completed");
        }

        TrainingMaster master = masterRepository.findByIdAndHospitalId(session.getTrainingMasterId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Training course not found"));

        List<TrainingAttendance> rows = attendanceRepository.findByHospitalIdAndSessionId(hospitalId, session.getId());
        int certified = 0;
        for (TrainingAttendance row : rows) {
            if (!"PRESENT".equals(row.getAttendanceStatus())) continue;
            row.setVerified(true);
            attendanceRepository.save(row);
            issueOrRenewCertification(hospitalId, row.getEmployeeId(), master, session.getId());
            certified++;
        }

        session.setStatus("COMPLETED");
        TrainingSession saved = sessionRepository.save(session);
        audit("TRAINING_SESSION_VERIFIED", "Session #" + session.getId() + " verified — " + certified + " attendee(s) certified", hospitalId);
        return saved;
    }

    private void issueOrRenewCertification(Long hospitalId, Long employeeId, TrainingMaster master, Long sessionId) {
        LocalDate completedAt = LocalDate.now();
        LocalDate expiresAt = master.getValidityPeriod() > 0 ? completedAt.plusMonths(master.getValidityPeriod()) : null;

        TrainingCertification cert = certificationRepository
                .findByHospitalIdAndEmployeeIdAndTrainingMasterId(hospitalId, employeeId, master.getId())
                .orElseGet(TrainingCertification::new);
        cert.setHospitalId(hospitalId);
        cert.setEmployeeId(employeeId);
        cert.setTrainingMasterId(master.getId());
        cert.setSessionId(sessionId);
        cert.setCompletedAt(completedAt);
        cert.setExpiresAt(expiresAt);
        cert.setCertificateRef("CERT-" + hospitalId + "-" + employeeId + "-" + master.getId());
        cert.setStatus("VALID");
        certificationRepository.save(cert);
    }

    /**
     * BR-7: verified attendance is only changed through this audited correction path, which
     * requires a reason and records the prior value.
     */
    @Transactional
    public TrainingAttendance correctAttendance(Long attendanceId, TrainingAttendanceCorrectRequest request) {
        Long hospitalId = requireHospital();
        TrainingAttendance attendance = attendanceRepository.findByIdAndHospitalId(attendanceId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("A correction reason is required (BR-7)");
        }
        String newStatus = request.getAttendanceStatus() == null ? "" : request.getAttendanceStatus().toUpperCase();
        if (!VALID_ATTENDANCE_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Attendance status must be one of " + VALID_ATTENDANCE_STATUSES);
        }

        String oldStatus = attendance.getAttendanceStatus();
        attendance.setAttendanceStatus(newStatus);
        attendance.setRemarks(request.getReason());
        TrainingAttendance saved = attendanceRepository.save(attendance);
        audit("TRAINING_ATTENDANCE_CORRECTED", "Attendance #" + attendance.getId() + " changed " + oldStatus
                + " -> " + newStatus + " (reason: " + request.getReason() + ")", hospitalId);
        return saved;
    }

    public List<TrainingAttendance> getEmployeeHistory(Long employeeId) {
        return attendanceRepository.findByHospitalIdAndEmployeeId(requireHospital(), employeeId);
    }

    /** BR-6: sweeps certifications past due_date/expiry window into EXPIRING/EXPIRED. */
    public List<TrainingCertification> getCertifications() {
        Long hospitalId = requireHospital();
        List<TrainingCertification> certs = certificationRepository.findByHospitalIdOrderByIdDesc(hospitalId);
        LocalDate today = LocalDate.now();
        for (TrainingCertification cert : certs) {
            if (cert.getExpiresAt() == null || "REVOKED".equals(cert.getStatus())) continue;
            String newStatus;
            if (cert.getExpiresAt().isBefore(today)) {
                newStatus = "EXPIRED";
            } else if (!cert.getExpiresAt().isAfter(today.plusDays(EXPIRING_WINDOW_DAYS))) {
                newStatus = "EXPIRING";
            } else {
                newStatus = "VALID";
            }
            if (!newStatus.equals(cert.getStatus())) {
                cert.setStatus(newStatus);
                certificationRepository.save(cert);
            }
        }
        return certs;
    }

    // ===== Helpers =====

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return hospitalId;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(securityHelper.getCurrentUserEmail());
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
