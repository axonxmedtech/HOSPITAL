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
        Opd opd = new Opd();

        Patient patient = patientRepository.findById(req.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid patient id"));
        opd.setPatient(patient);

        // Set receptionist from authenticated user (do not trust client-supplied receptionistId)
        try {
            Long receptionistId = securityHelper.getCurrentUserId();
            if (receptionistId != null) {
                userRepository.findById(receptionistId).ifPresent(opd::setReceptionist);
            }
        } catch (Exception ignored) {
            // If no authenticated user in context, skip setting receptionist
        }

        if (req.getDoctorId() != null) {
            doctorRepository.findById(req.getDoctorId()).ifPresent(opd::setDoctor);
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
            } catch (Exception ignored) {}
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
            try { performedBy = securityHelper.getCurrentUserEmail(); } catch (Exception ignored) {}
            Long hospitalId = null;
            try { hospitalId = securityHelper.getCurrentHospitalId(); } catch (Exception ignored) {}

            String details = "OPD " + (saved.getCaseId() != null ? saved.getCaseId() : saved.getId())
                    + " created for patient " + (saved.getPatient() != null ? saved.getPatient().getId() : "-");

            auditLogService.logAction(
                    "OPD_CREATED",
                    details,
                    performedBy,
                    hospitalId,
                    "OPD",
                    saved.getCaseId() != null ? saved.getCaseId() : (saved.getId() != null ? saved.getId().toString() : null),
                    null
            );
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {}
        return queueEntryRepository.findQueueForDoctorToday(doctorId);
    }

    public Opd getOpdById(Long id) {
        return opdRepository.findById(id).orElse(null);
    }

    public org.springframework.data.domain.Page<Opd> getOpds(String search, String dateStr, org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception ignored) {}

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
            } catch (Exception ignored) {}
        }

        String searchVal = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        return opdRepository.searchByHospitalAndDateRange(hospitalId, searchVal, startDate, endDate, pageable);
    }

    public java.util.List<QueueEntry> getHospitalQueue() {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception ignored) {}
        if (hospitalId == null) return java.util.List.of();
        try {
            autoQueueTodaysFollowupsForHospital(hospitalId);
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
