package com.hms.service.hospital;

import com.hms.dto.CreateOpdRequest;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class OpdService {

    private static final Logger logger = LoggerFactory.getLogger(OpdService.class);

    private final OpdRepository opdRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;
    private final HospitalWebSocketHandler webSocketHandler;

    public OpdService(OpdRepository opdRepository,
                      QueueEntryRepository queueEntryRepository,
                      PatientRepository patientRepository,
                      DoctorRepository doctorRepository,
                      UserRepository userRepository,
                      MedicalRecordRepository medicalRecordRepository,
                      SecurityContextHelper securityHelper,
                      AuditLogService auditLogService,
                      HospitalWebSocketHandler webSocketHandler) {
        this.opdRepository = opdRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public Opd createOpd(CreateOpdRequest req) {
        // Validate Vitals
        if (req.getBp() != null && !req.getBp().trim().isEmpty()) {
            String bp = req.getBp().trim();
            if (!bp.matches("^\\d{2,3}\\s*/\\s*\\d{2,3}$")) {
                throw new IllegalArgumentException("Blood pressure must be in format Systolic/Diastolic, e.g., 120/80");
            }
            String[] parts = bp.split("/");
            int systolic = Integer.parseInt(parts[0].trim());
            int diastolic = Integer.parseInt(parts[1].trim());
            if (systolic <= diastolic) {
                throw new IllegalArgumentException("Systolic blood pressure must be greater than diastolic blood pressure");
            }
        }
        if (req.getTemperature() != null && (req.getTemperature() < 30.0 || req.getTemperature() > 45.0)) {
            throw new IllegalArgumentException("Temperature must be between 30.0°C and 45.0°C");
        }
        if (req.getPulse() != null && (req.getPulse() < 30 || req.getPulse() > 250)) {
            throw new IllegalArgumentException("Pulse must be between 30 and 250 bpm");
        }
        if (req.getWeight() != null && (req.getWeight() < 0.1 || req.getWeight() > 500.0)) {
            throw new IllegalArgumentException("Weight must be between 0.1 and 500.0 kg");
        }
        if (req.getSpo2() != null && (req.getSpo2() < 0 || req.getSpo2() > 100)) {
            throw new IllegalArgumentException("SpO2 must be between 0% and 100%");
        }

        Opd opd = new Opd();
        Patient patient;
        if (req.getPatientId() != null && !req.getPatientId().trim().isEmpty()) {
            String pid = req.getPatientId().trim();
            Long hospitalId = securityHelper.getCurrentHospitalId();
            if (pid.matches("^\\d+$")) {
                Long numericId = Long.parseLong(pid);
                patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(numericId, hospitalId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid patient id"));
            } else {
                patient = patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(pid, hospitalId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid patient id"));
            }
        } else {
            throw new IllegalArgumentException("Patient ID is required");
        }
        opd.setPatient(patient);

        // Set receptionist from authenticated user (do not trust client-supplied receptionistId)
        try {
            Long receptionistId = securityHelper.getCurrentUserId();
            if (receptionistId != null) {
                userRepository.findById(receptionistId).ifPresent(opd::setReceptionist);
            }
        } catch (Exception e) {
            logger.warn("Could not resolve current user as receptionist; continuing without receptionist assignment", e);
        }

        if (req.getDoctorId() != null && !req.getDoctorId().trim().isEmpty()) {
            String docIdStr = req.getDoctorId().trim();
            java.util.Optional<Doctor> docOpt = java.util.Optional.empty();
            if (docIdStr.matches("^\\d+$")) {
                Long numericId = Long.parseLong(docIdStr);
                docOpt = doctorRepository.findByIdOrUserId(numericId, userRepository);
            } else {
                Long hospitalId = securityHelper.getCurrentHospitalId();
                docOpt = doctorRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(docIdStr, hospitalId);
            }
            docOpt.ifPresent(opd::setDoctor);
        }

        opd.setBp(req.getBp());
        opd.setTemperature(req.getTemperature());
        opd.setPulse(req.getPulse());
        opd.setWeight(req.getWeight());
        opd.setSpo2(req.getSpo2());
        opd.setProblem(req.getProblem());
        if (req.getVisitType() != null) {
            try {
                opd.setVisitType(Opd.VisitType.valueOf(req.getVisitType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid visit type '{}'; defaulting to WALKIN", req.getVisitType());
                opd.setVisitType(Opd.VisitType.WALKIN);
            }
        }

        Opd saved = opdRepository.save(opd);
        // Set sequential caseId using auto-increment id: OPD-1, OPD-2, OPD-3...
        saved.setCaseId("OPD-" + saved.getId());
        saved = opdRepository.save(saved);

        // Create queue entry
        if (saved.getDoctor() != null) {
            QueueEntry entry = new QueueEntry();
            entry.setOpd(saved);
            entry.setDoctor(saved.getDoctor());
            queueEntryRepository.save(entry);
        }

        // Audit log for OPD creation
        try {
            String performedBy = null;
            try { performedBy = securityHelper.getCurrentUserEmail(); } catch (Exception e) {
                logger.debug("Could not resolve current user email for audit log", e);
            }
            Long auditHospitalId = null;
            try { auditHospitalId = securityHelper.getCurrentHospitalId(); } catch (Exception e) {
                logger.debug("Could not resolve hospital ID for audit log", e);
            }

            String details = "OPD " + (saved.getCaseId() != null ? saved.getCaseId() : saved.getId())
                    + " created for patient " + (saved.getPatient() != null ? saved.getPatient().getId() : "-");

            auditLogService.logAction(
                    "OPD_CREATED",
                    details,
                    performedBy,
                    auditHospitalId,
                    "OPD",
                    saved.getCaseId() != null ? saved.getCaseId() : (saved.getId() != null ? saved.getId().toString() : null),
                    null
            );
        } catch (Exception e) {
            logger.warn("Failed to write audit log for OPD creation", e);
        }

        // Broadcast real-time update to all connected clients in this hospital
        try {
            Long broadcastHospitalId = securityHelper.getCurrentHospitalId();
            if (broadcastHospitalId != null) {
                webSocketHandler.broadcast(broadcastHospitalId, "{\"type\":\"REFRESH_DATA\"}");
            }
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after OPD creation", e);
        }

        return saved;
    }

    public java.util.List<QueueEntry> getQueueForDoctor(Long doctorId) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            if (hospitalId != null) {
                autoQueueTodaysFollowupsForDoctor(hospitalId, doctorId);
            }
        } catch (Exception e) {
            logger.warn("Failed to auto-queue today's follow-ups for doctor {}", doctorId, e);
        }
        return queueEntryRepository.findQueueForDoctorToday(doctorId);
    }

    public Opd getOpdById(Long id) {
        return opdRepository.findById(id).orElse(null);
    }

    public org.springframework.data.domain.Page<Opd> getOpds(String search, String dateStr, com.hms.entity.Opd.Status status, org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception e) {
            logger.warn("Could not resolve hospital ID for OPD listing", e);
        }

        if (hospitalId == null) {
            // Fallback: return empty page
            return new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        }

        java.time.LocalDateTime startDate = null;
        java.time.LocalDateTime endDate = null;
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr.trim());
                startDate = date.atStartOfDay();
                endDate = date.atTime(23, 59, 59, 999999999);
            } catch (Exception e) {
                logger.warn("Invalid date filter '{}' ignored for OPD listing", dateStr);
            }
        }

        String searchVal = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        return opdRepository.searchByHospitalAndDateRange(hospitalId, searchVal, startDate, endDate, status, pageable);
    }

    public org.springframework.data.domain.Page<Opd> getOpds(String search, String dateStr, org.springframework.data.domain.Pageable pageable) {
        return getOpds(search, dateStr, null, pageable);
    }

    public java.util.List<QueueEntry> getHospitalQueue() {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception e) {
            logger.warn("Could not resolve hospital ID for queue listing", e);
        }
        if (hospitalId == null) return java.util.List.of();
        try {
            autoQueueTodaysFollowupsForHospital(hospitalId);
        } catch (Exception e) {
            logger.warn("Failed to auto-queue today's follow-ups for hospital {}", hospitalId, e);
        }
        return queueEntryRepository.findQueueForHospitalToday(hospitalId);
    }

    @org.springframework.transaction.annotation.Transactional
    public void autoQueueTodaysFollowupsForHospital(Long hospitalId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime startOfToday = today.atStartOfDay();

        java.util.List<com.hms.entity.MedicalRecord> records = medicalRecordRepository.findByHospitalIdAndFollowUpDate(hospitalId, today);
        for (com.hms.entity.MedicalRecord record : records) {
            boolean alreadyQueued = opdRepository.existsByPatientIdAndVisitTypeAndCreatedAtGreaterThanEqual(
                    record.getPatientId(),
                    com.hms.entity.Opd.VisitType.FOLLOWUP,
                    startOfToday
            );
            if (!alreadyQueued) {
                queueFollowUp(record);
            }
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void autoQueueTodaysFollowupsForDoctor(Long hospitalId, Long doctorId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime startOfToday = today.atStartOfDay();

        java.util.List<com.hms.entity.MedicalRecord> records = medicalRecordRepository.findByHospitalIdAndDoctorIdAndFollowUpDate(hospitalId, doctorId, today);
        for (com.hms.entity.MedicalRecord record : records) {
            boolean alreadyQueued = opdRepository.existsByPatientIdAndVisitTypeAndCreatedAtGreaterThanEqual(
                    record.getPatientId(),
                    com.hms.entity.Opd.VisitType.FOLLOWUP,
                    startOfToday
            );
            if (!alreadyQueued) {
                queueFollowUp(record);
            }
        }
    }

    private void queueFollowUp(com.hms.entity.MedicalRecord record) {
        com.hms.entity.Opd opd = new com.hms.entity.Opd();

        com.hms.entity.Patient patient = patientRepository.findById(record.getPatientId()).orElse(null);
        if (patient == null) return;
        opd.setPatient(patient);

        com.hms.entity.Doctor doctor = doctorRepository.findById(record.getDoctorId()).orElse(null);
        if (doctor == null) return;
        opd.setDoctor(doctor);

        opd.setVisitType(com.hms.entity.Opd.VisitType.FOLLOWUP);
        opd.setProblem(record.getDiagnosis() != null ? "Follow-up: " + record.getDiagnosis() : "Follow-up");
        opd.setStatus(com.hms.entity.Opd.Status.QUEUED);

        com.hms.entity.Opd saved = opdRepository.save(opd);
        saved.setCaseId("OPD-" + saved.getId());
        saved = opdRepository.save(saved);

        com.hms.entity.QueueEntry entry = new com.hms.entity.QueueEntry();
        entry.setOpd(saved);
        entry.setDoctor(doctor);
        queueEntryRepository.save(entry);

        try {
            String details = "OPD Follow-up " + saved.getCaseId() + " auto-created for patient " + patient.getId();
            auditLogService.logAction(
                    "OPD_CREATED",
                    details,
                    "SYSTEM",
                    record.getHospitalId(),
                    "OPD",
                    saved.getCaseId(),
                    null
            );
        } catch (Exception e) {
            logger.warn("Failed to write audit log for follow-up OPD creation {}", saved.getCaseId(), e);
        }
    }

    public java.util.List<com.hms.entity.MedicalRecord> getFollowUpsForDoctorToday(Long hospitalId, Long doctorId, java.time.LocalDate today) {
        return medicalRecordRepository.findByHospitalIdAndDoctorIdAndFollowUpDate(hospitalId, doctorId, today);
    }

    public java.util.List<com.hms.entity.MedicalRecord> getFollowUpsForHospitalToday(Long hospitalId, java.time.LocalDate today) {
        return medicalRecordRepository.findByHospitalIdAndFollowUpDate(hospitalId, today);
    }

    public java.util.Optional<java.util.Map<String, String>> getPatientNameAndCustomIdAndPublicId(Long patientId) {
        return patientRepository.findById(patientId).map(p -> {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("name", p.getName());
            map.put("customId", p.getCustomId());
            map.put("publicId", p.getPublicId());
            return map;
        });
    }

    public java.util.Optional<String> getDoctorName(Long doctorId) {
        return doctorRepository.findById(doctorId).map(com.hms.entity.Doctor::getName);
    }
}
