package com.hms.service.hospital;

import com.hms.dto.CreateSurgeryRequest;
import com.hms.dto.ScheduleSurgeryRequest;
import com.hms.dto.SurgeryView;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SurgeryService - OT case lifecycle (OT module, Phase 2). Doctors create
 * requests; reception schedules/starts/completes. HOSPITAL tenant only, OT-gated
 * at the controller. One active (non-terminal) surgery per IPD admission.
 */
@Service
public class SurgeryService {

    private static final Logger logger = LoggerFactory.getLogger(SurgeryService.class);
    private static final Set<String> ACTIVE_STATUSES =
            Set.of(Surgery.REQUESTED, Surgery.SCHEDULED, Surgery.IN_PROGRESS);

    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientNurseAssignmentRepository assignmentRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogService auditLogService;
    // Nursing Mgmt Phase C2: all bed status writes go through the audited service.
    @Autowired private BedStatusService bedStatusService;

    // ---------- Doctor: create request ----------

    @Transactional
    public Surgery createRequest(CreateSurgeryRequest req) {
        Long hospitalId = requireHospitalId();
        if (req.getIpdAdmissionId() == null) throw new IllegalArgumentException("ipdAdmissionId is required");
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);

        if (!surgeryRepository.findByIpdAdmissionIdAndStatusIn(admission.getId(), ACTIVE_STATUSES).isEmpty()) {
            throw new IllegalArgumentException("This patient already has an active surgery");
        }
        if (req.getProcedureName() == null || req.getProcedureName().trim().isEmpty()) {
            throw new IllegalArgumentException("Procedure name is required");
        }

        Long doctorId = doctorRepository
                .findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                .map(Doctor::getId).orElse(null);

        Surgery s = new Surgery();
        s.setHospitalId(hospitalId);
        s.setIpdAdmissionId(admission.getId());
        s.setPatientId(admission.getPatientId());
        s.setProcedureName(req.getProcedureName().trim());
        s.setClinicalNotes(trim(req.getClinicalNotes()));
        s.setPriority("EMERGENCY".equalsIgnoreCase(req.getPriority()) ? "EMERGENCY" : "ELECTIVE");
        s.setPreferredDate(req.getPreferredDate());
        s.setRequestedByDoctorId(doctorId);
        s.setRequestedByUserId(securityHelper.getCurrentUserId());
        s.setRequestedAt(LocalDateTime.now());
        s.setStatus(Surgery.REQUESTED);
        Surgery saved = surgeryRepository.save(s);

        audit("SURGERY_REQUESTED", "Surgery requested for IPD " + admission.getIpdNumber(), hospitalId, admission.getId());
        return saved;
    }

    // ---------- Reception: schedule ----------

    @Transactional
    public Surgery schedule(String publicId, ScheduleSurgeryRequest req) {
        Long hospitalId = requireHospitalId();
        Surgery s = requireSurgery(publicId, hospitalId);
        if (!Surgery.REQUESTED.equals(s.getStatus())) {
            throw new IllegalArgumentException("Only a requested surgery can be scheduled");
        }
        if (req.getScheduledAt() == null || req.getOtWardId() == null) {
            throw new IllegalArgumentException("Date/time and OT ward are required");
        }

        // Any active doctor may be assigned as the operating surgeon; reception decides.
        // If no doctor id is given ("Other"), a free-text operator name is required
        // instead (e.g. external / anaesthetist-led case).
        Doctor surgeon = null;
        String surgeonDisplayName;
        if (req.getSurgeonDoctorId() != null) {
            surgeon = doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(req.getSurgeonDoctorId(), hospitalId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
            surgeonDisplayName = surgeon.getName();
        } else {
            if (req.getSurgeonName() == null || req.getSurgeonName().trim().isEmpty()) {
                throw new IllegalArgumentException("Select a doctor, or choose Other and enter the operator's name");
            }
            surgeonDisplayName = req.getSurgeonName().trim();
        }
        Ward ward = wardRepository.findById(req.getOtWardId())
                .orElseThrow(() -> new IllegalArgumentException("OT ward not found"));
        if (!hospitalId.equals(ward.getHospitalId())) {
            throw new UnauthorizedException("Access denied: ward belongs to another hospital");
        }
        // An OT ward has a single bed: reject if it already holds a scheduled or live
        // surgery, so reception cannot double-book the theatre.
        boolean otBusy = !surgeryRepository.findByOtWardIdAndStatus(ward.getWardId(), Surgery.SCHEDULED).isEmpty()
                || !surgeryRepository.findByOtWardIdAndStatus(ward.getWardId(), Surgery.IN_PROGRESS).isEmpty();
        if (otBusy) {
            throw new IllegalArgumentException("That OT ward already has a scheduled or ongoing surgery");
        }

        s.setSurgeonDoctorId(surgeon != null ? surgeon.getId() : null);
        s.setSurgeonName(surgeonDisplayName);
        s.setAnaesthetistName(trim(req.getAnaesthetistName()));
        s.setScheduledAt(req.getScheduledAt());
        s.setOtWardId(ward.getWardId());
        s.setScheduledByUserId(securityHelper.getCurrentUserId());
        s.setStatus(Surgery.SCHEDULED);
        Surgery saved = surgeryRepository.save(s);

        notifyNurse(saved, hospitalId, surgeonDisplayName);
        if (surgeon != null) notifySurgeon(saved, hospitalId, surgeon);
        audit("SURGERY_SCHEDULED", "Surgery scheduled (operator " + surgeonDisplayName + ")", hospitalId, saved.getIpdAdmissionId());
        return saved;
    }

    // ---------- Reception: start / complete / cancel ----------

    @Transactional
    public Surgery start(String publicId) {
        Long hospitalId = requireHospitalId();
        Surgery s = requireSurgery(publicId, hospitalId);
        if (!Surgery.SCHEDULED.equals(s.getStatus())) {
            throw new IllegalArgumentException("Only a scheduled surgery can be started");
        }
        Bed bed = bedRepository.findByWardIdAndHospitalId(s.getOtWardId(), hospitalId).stream()
                .filter(b -> "available".equalsIgnoreCase(b.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OT theatre is busy or has no available bed"));
        bedStatusService.change(bed.getBedId(), com.hms.entity.BedStatus.OCCUPIED, "Surgery started");
        bed.setCurrentIpdAdmissionId(s.getIpdAdmissionId());
        bedRepository.save(bed);

        s.setOtBedId(bed.getBedId());
        s.setStartedAt(LocalDateTime.now());
        s.setStatus(Surgery.IN_PROGRESS);
        Surgery saved = surgeryRepository.save(s);
        audit("SURGERY_STARTED", "Surgery started", hospitalId, saved.getIpdAdmissionId());
        return saved;
    }

    @Transactional
    public Surgery complete(String publicId) {
        Long hospitalId = requireHospitalId();
        Surgery s = requireSurgery(publicId, hospitalId);
        if (!Surgery.IN_PROGRESS.equals(s.getStatus())) {
            throw new IllegalArgumentException("Only a live surgery can be completed");
        }
        freeOtBed(s, "Surgery completed");
        s.setCompletedAt(LocalDateTime.now());
        s.setStatus(Surgery.COMPLETED);
        Surgery saved = surgeryRepository.save(s);
        audit("SURGERY_COMPLETED", "Surgery completed", hospitalId, saved.getIpdAdmissionId());
        return saved;
    }

    @Transactional
    public Surgery cancel(String publicId) {
        Long hospitalId = requireHospitalId();
        Surgery s = requireSurgery(publicId, hospitalId);
        if (Surgery.COMPLETED.equals(s.getStatus()) || Surgery.CANCELLED.equals(s.getStatus())) {
            throw new IllegalArgumentException("Surgery is already closed");
        }
        if (Surgery.IN_PROGRESS.equals(s.getStatus())) freeOtBed(s, "Surgery cancelled");
        s.setStatus(Surgery.CANCELLED);
        Surgery saved = surgeryRepository.save(s);
        audit("SURGERY_CANCELLED", "Surgery cancelled", hospitalId, saved.getIpdAdmissionId());
        return saved;
    }

    // ---------- Reads ----------

    /** Reception "Requests" filter. */
    public List<SurgeryView> listRequests() {
        Long hospitalId = requireHospitalId();
        return decorate(surgeryRepository.findByHospitalIdAndStatusOrderByRequestedAtDesc(hospitalId, Surgery.REQUESTED));
    }

    /** Reception "Scheduled/Live" filter. */
    public List<SurgeryView> listBoard() {
        Long hospitalId = requireHospitalId();
        return decorate(surgeryRepository.findByHospitalIdAndStatusInOrderByScheduledAtAsc(
                hospitalId, List.of(Surgery.SCHEDULED, Surgery.IN_PROGRESS)));
    }

    /** Doctor board: scheduled+live surgeries where they are the assigned surgeon or requester. */
    public List<SurgeryView> listMyBoard() {
        Long hospitalId = requireHospitalId();
        Long myDoctorId = doctorRepository
                .findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                .map(Doctor::getId).orElse(null);
        List<Surgery> all = surgeryRepository.findByHospitalIdAndStatusInOrderByScheduledAtAsc(
                hospitalId, List.of(Surgery.SCHEDULED, Surgery.IN_PROGRESS));
        List<Surgery> mine = all.stream()
                .filter(s -> Objects.equals(s.getSurgeonDoctorId(), myDoctorId)
                        || Objects.equals(s.getRequestedByDoctorId(), myDoctorId))
                .collect(Collectors.toList());
        return decorate(mine);
    }

    /** Active surgery for an IPD admission (for the doctor IPD case view); null if none. */
    public SurgeryView getActiveForAdmission(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        requireAdmission(ipdAdmissionId, hospitalId);
        return surgeryRepository.findByIpdAdmissionIdAndStatusIn(ipdAdmissionId, ACTIVE_STATUSES).stream()
                .findFirst().map(s -> decorate(List.of(s)).get(0)).orElse(null);
    }

    /** Doctor dropdown for reception scheduling — any active doctor can be assigned. */
    public List<Map<String, Object>> listSurgeons() {
        Long hospitalId = requireHospitalId();
        return doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId).stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("doctorId", d.getId());
                    m.put("name", d.getName());
                    m.put("specialization", d.getSpecialization());
                    return m;
                }).collect(Collectors.toList());
    }

    // ---------- helpers ----------

    // Nursing Mgmt Phase C2: the theatre bed is marked for cleaning (not
    // immediately available) so the next surgery cannot start until it is cleaned.
    private void freeOtBed(Surgery s, String remark) {
        if (s.getOtBedId() == null) return;
        try {
            bedStatusService.change(s.getOtBedId(), com.hms.entity.BedStatus.CLEANING, remark);
        } catch (Exception e) {
            logger.warn("Failed to mark OT bed for cleaning: {}", e.getMessage());
        }
    }

    private void notifyNurse(Surgery s, Long hospitalId, String operatorName) {
        try {
            assignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(s.getIpdAdmissionId()).ifPresent(asg -> {
                Long patientId = ipdAdmissionRepository.findById(s.getIpdAdmissionId())
                        .map(IpdAdmission::getPatientId).orElse(s.getPatientId());
                String patientName = patientRepository.findById(patientId)
                        .map(Patient::getName).orElse("your patient");
                notificationService.create(
                        asg.getNurseUserId(), hospitalId, "OT_SCHEDULED",
                        "Surgery scheduled",
                        "Surgery (" + safe(s.getProcedureName()) + ") scheduled for " + patientName
                                + " on " + s.getScheduledAt() + " with " + safe(operatorName),
                        "SURGERY", s.getId());
            });
        } catch (Exception e) {
            logger.warn("Failed to notify nurse of OT schedule: {}", e.getMessage());
        }
    }

    private void notifySurgeon(Surgery s, Long hospitalId, Doctor surgeon) {
        try {
            if (surgeon.getEmail() == null) return;
            userRepository.findByEmail(surgeon.getEmail()).ifPresent(u ->
                    notificationService.create(
                            u.getId(), hospitalId, "OT_ASSIGNED",
                            "You are assigned a surgery",
                            "You are scheduled to operate (" + safe(s.getProcedureName()) + ") on " + s.getScheduledAt(),
                            "SURGERY", s.getId()));
        } catch (Exception e) {
            logger.warn("Failed to notify surgeon of OT assignment: {}", e.getMessage());
        }
    }

    private List<SurgeryView> decorate(List<Surgery> list) {
        Map<Long, String> ipdNumbers = new HashMap<>();
        Map<Long, Patient> patients = new HashMap<>();
        Map<Long, String> doctorNames = new HashMap<>();
        Map<Long, String> wardNames = new HashMap<>();
        return list.stream().map(s -> {
            SurgeryView v = SurgeryView.of(s);
            v.setIpdNumber(ipdNumbers.computeIfAbsent(s.getIpdAdmissionId(), id ->
                    ipdAdmissionRepository.findById(id).map(IpdAdmission::getIpdNumber).orElse(null)));
            Patient p = patients.computeIfAbsent(s.getPatientId(), id ->
                    patientRepository.findById(id).orElse(null));
            if (p != null) {
                v.setPatientName(p.getName());
                v.setPatientAge(p.getAge());
                v.setPatientSex(p.getGender());
            }
            // Prefer the stored operator name (covers the "Other" case); fall back to
            // the assigned doctor's current name for older records.
            if (s.getSurgeonName() != null && !s.getSurgeonName().isBlank()) {
                v.setSurgeonName(s.getSurgeonName());
            } else if (s.getSurgeonDoctorId() != null) {
                v.setSurgeonName(doctorNames.computeIfAbsent(s.getSurgeonDoctorId(), id ->
                        doctorRepository.findById(id).map(Doctor::getName).orElse(null)));
            }
            if (s.getRequestedByDoctorId() != null) {
                v.setRequestedByName(doctorNames.computeIfAbsent(s.getRequestedByDoctorId(), id ->
                        doctorRepository.findById(id).map(Doctor::getName).orElse(null)));
            }
            if (s.getOtWardId() != null) {
                v.setOtWardName(wardNames.computeIfAbsent(s.getOtWardId(), id ->
                        wardRepository.findById(id).map(Ward::getWardName).orElse(null)));
            }
            return v;
        }).collect(Collectors.toList());
    }

    private Surgery requireSurgery(String publicId, Long hospitalId) {
        Surgery s = surgeryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Surgery not found"));
        if (!hospitalId.equals(s.getHospitalId())) {
            throw new UnauthorizedException("Access denied: surgery belongs to another hospital");
        }
        return s;
    }

    private IpdAdmission requireAdmission(Long ipdAdmissionId, Long hospitalId) {
        IpdAdmission a = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        if (!hospitalId.equals(a.getHospitalId())) {
            throw new UnauthorizedException("Access denied: admission belongs to another hospital");
        }
        return a;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }

    private String trim(String s) { return s == null ? null : s.trim(); }
    private String safe(String s) { return s == null ? "" : s; }

    private void audit(String action, String details, Long hospitalId, Long admissionId) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), hospitalId,
                    "SURGERY", admissionId != null ? admissionId.toString() : null, null);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for {}", action, e);
        }
    }
}
