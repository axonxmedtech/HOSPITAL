package com.hms.service.hospital;

import com.hms.dto.CreateOpdRequest;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OpdService {

    private final OpdRepository opdRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public OpdService(OpdRepository opdRepository,
                      QueueEntryRepository queueEntryRepository,
                      PatientRepository patientRepository,
                      DoctorRepository doctorRepository,
                      UserRepository userRepository,
                      SecurityContextHelper securityHelper,
                      AuditLogService auditLogService) {
        this.opdRepository = opdRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Opd createOpd(CreateOpdRequest req) {
        Opd opd = new Opd();
        // Generate case id
        opd.setCaseId("OPD-" + System.currentTimeMillis());

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

        // Determine token number for the assigned doctor (per-day)
        Integer nextToken = 1;
        if (opd.getDoctor() != null) {
            Integer max = queueEntryRepository.findMaxTokenForDoctorToday(opd.getDoctor().getId());
            nextToken = (max == null ? 0 : max) + 1;
        }
        opd.setTokenNumber(nextToken);

        Opd saved = opdRepository.save(opd);

        // Create queue entry
        if (saved.getDoctor() != null) {
            QueueEntry entry = new QueueEntry();
            entry.setOpd(saved);
            entry.setDoctor(saved.getDoctor());
            entry.setTokenNumber(saved.getTokenNumber());
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

        return saved;
    }

    public java.util.List<QueueEntry> getQueueForDoctor(Long doctorId) {
        return queueEntryRepository.findQueueForDoctorToday(doctorId);
    }

    public Opd getOpdById(Long id) {
        return opdRepository.findById(id).orElse(null);
    }

    public org.springframework.data.domain.Page<Opd> getOpds(String search, org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception ignored) {}

        if (hospitalId == null) {
            // Fallback: return empty page
            return new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        }

        if (search != null && !search.trim().isEmpty()) {
            return opdRepository.searchByHospital(hospitalId, search.trim(), pageable);
        }
        return opdRepository.findByPatient_HospitalId(hospitalId, pageable);
    }

    public java.util.List<QueueEntry> getHospitalQueue() {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception ignored) {}
        if (hospitalId == null) return java.util.List.of();
        return queueEntryRepository.findQueueForHospitalToday(hospitalId);
    }
}
