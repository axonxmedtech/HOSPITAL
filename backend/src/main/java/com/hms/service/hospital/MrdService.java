package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.MrdRecord;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.MrdRecordRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MrdService {

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.UserRepository userRepository;

    @Autowired
    private MrdRecordRepository mrdRecordRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.DischargeSummaryRepository dischargeSummaryRepository;

    @Autowired
    private com.hms.repository.VitalSignsRepository vitalSignsRepository;

    @Autowired
    private com.hms.repository.DoctorRoundRepository doctorRoundRepository;

    @Autowired
    private com.hms.repository.PatientConsentRepository patientConsentRepository;

    @Autowired
    private com.hms.repository.ClinicalAssessmentRepository clinicalAssessmentRepository;

    @Autowired
    private com.hms.repository.EmergencyVisitRepository emergencyVisitRepository;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    public List<com.hms.dto.MrdPendingDTO> listPendingArchive() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<IpdAdmission> discharged = ipdAdmissionRepository.findByHospitalIdAndStatus(hospitalId, "DISCHARGED");
        List<com.hms.dto.MrdPendingDTO> pending = new ArrayList<>();
        for (IpdAdmission ipd : discharged) {
            if (!mrdRecordRepository.findByIpdAdmissionId(ipd.getId()).isPresent()) {
                com.hms.dto.MrdPendingDTO dto = new com.hms.dto.MrdPendingDTO();
                dto.ipdAdmissionId = ipd.getId();
                dto.ipdNumber = ipd.getIpdNumber();
                dto.admissionDateTime = ipd.getAdmissionDatetime();
                dto.dischargeDateTime = ipd.getDischargeDatetime();
                
                patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                    dto.patientName = p.getName();
                    dto.patientGender = p.getGender();
                    try { dto.patientAge = p.getAge(); } catch (Exception ignored) {}
                });

                if (ipd.getDoctorId() != null) {
                    doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.doctorName = d.getName());
                }
                pending.add(dto);
            }
        }
        return pending;
    }

    public List<com.hms.dto.MrdArchivedDTO> listArchived() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<MrdRecord> archived = mrdRecordRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        List<com.hms.dto.MrdArchivedDTO> list = new ArrayList<>();
        for (MrdRecord m : archived) {
            com.hms.dto.MrdArchivedDTO dto = new com.hms.dto.MrdArchivedDTO();
            dto.id = m.getId();
            dto.ipdAdmissionId = m.getIpdAdmissionId();
            dto.mrdNumber = m.getMrdNumber();
            dto.rackLocation = m.getRackLocation();
            dto.archivedAt = m.getArchivedAt();

            ipdAdmissionRepository.findById(m.getIpdAdmissionId()).ifPresent(ipd -> {
                dto.ipdNumber = ipd.getIpdNumber();
                patientRepository.findById(ipd.getPatientId()).ifPresent(p -> dto.patientName = p.getName());
                if (ipd.getDoctorId() != null) {
                    doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.doctorName = d.getName());
                }
            });

            userRepository.findById(m.getArchivedById()).ifPresent(u -> dto.archivedByName = u.getName());
            list.add(dto);
        }
        return list;
    }

    @Transactional
    /**
     * Form 02: server-computed IPD-file completeness checklist, read directly from
     * the clinical source tables. Tenant-guarded via the admission.
     */
    public com.hms.dto.MrdCompletenessDTO computeCompleteness(Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (!ipd.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        com.hms.dto.MrdCompletenessDTO dto = new com.hms.dto.MrdCompletenessDTO();
        dto.setIpdAdmissionId(ipdAdmissionId);

        boolean hasDischargeSummary = dischargeSummaryRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent();
        boolean hasVitals = !vitalSignsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(ipdAdmissionId).isEmpty();
        boolean hasRounds = !doctorRoundRepository
                .findByIpdAdmissionIdAndHospitalIdOrderByRoundDateTimeDesc(ipdAdmissionId, hospitalId).isEmpty();
        boolean hasConsent = !patientConsentRepository
                .findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, ipdAdmissionId).isEmpty();
        boolean hasAssessment = clinicalAssessmentRepository
                .findFirstByHospitalIdAndAdmissionIdAndStatusNotInOrderByVersionDesc(
                        hospitalId, ipdAdmissionId, List.of("DELETED"))
                .isPresent();

        dto.getItems().put("DISCHARGE_SUMMARY", hasDischargeSummary ? "PASS" : "FAIL");
        dto.getItems().put("INITIAL_ASSESSMENT", hasAssessment ? "PASS" : "FAIL");
        dto.getItems().put("VITALS_CHART", hasVitals ? "PASS" : "FAIL");
        dto.getItems().put("DOCTOR_ROUNDS", hasRounds ? "PASS" : "FAIL");
        dto.getItems().put("CONSENT", hasConsent ? "PASS" : "FAIL");
        dto.setComplete(hasDischargeSummary && hasVitals && hasRounds && hasConsent && hasAssessment);
        return dto;
    }

    /**
     * Form 31: longitudinal patient EMR timeline — admissions, discharges,
     * emergency visits and clinical assessments merged chronologically.
     */
    public List<com.hms.dto.TimelineEventDTO> getPatientTimeline(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        com.hms.entity.Patient patient = patientRepository.findById(patientId)
                .filter(p -> p.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new RuntimeException("Patient not found under hospital tenant"));

        List<com.hms.dto.TimelineEventDTO> events = new ArrayList<>();

        for (IpdAdmission adm : ipdAdmissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(patient.getId())) {
            if (!hospitalId.equals(adm.getHospitalId())) continue; // defense-in-depth
            events.add(new com.hms.dto.TimelineEventDTO("ADMISSION", adm.getAdmissionDatetime(),
                    "IPD Admission " + adm.getIpdNumber(),
                    adm.getPrimaryDiagnosis() != null ? adm.getPrimaryDiagnosis() : "", adm.getId()));
            if (adm.getDischargeDatetime() != null) {
                events.add(new com.hms.dto.TimelineEventDTO("DISCHARGE", adm.getDischargeDatetime(),
                        "Discharged (" + adm.getIpdNumber() + ")", "", adm.getId()));
            }
        }
        for (com.hms.entity.EmergencyVisit ev : emergencyVisitRepository
                .findByPatientIdAndHospitalIdOrderByArrivalTimeDesc(patient.getId(), hospitalId)) {
            events.add(new com.hms.dto.TimelineEventDTO("EMERGENCY", ev.getArrivalTime(),
                    "Emergency visit " + ev.getEmergencyNumber(),
                    (ev.getTriageLevel() != null ? "Triage " + ev.getTriageLevel() : "")
                            + (ev.getInitialDiagnosis() != null ? " — " + ev.getInitialDiagnosis() : ""), ev.getId()));
        }
        for (com.hms.entity.ClinicalAssessment ca : clinicalAssessmentRepository
                .findByHospitalIdAndPatientId(hospitalId, patient.getId())) {
            events.add(new com.hms.dto.TimelineEventDTO("ASSESSMENT", ca.getCreatedAt(),
                    "Clinical assessment",
                    ca.getChiefComplaint() != null ? ca.getChiefComplaint() : "", ca.getId()));
        }

        events.sort((a, b) -> {
            if (a.getEventTime() == null) return 1;
            if (b.getEventTime() == null) return -1;
            return b.getEventTime().compareTo(a.getEventTime());
        });
        return events;
    }

    public MrdRecord archiveAdmission(Long ipdAdmissionId, String rackLocation) {
        return archiveAdmission(ipdAdmissionId, rackLocation, null);
    }

    public MrdRecord archiveAdmission(Long ipdAdmissionId, String rackLocation, String overrideReason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        Long userId = securityHelper.getCurrentUserId();
        if (userId == null) throw new UnauthorizedException("User ID not found");

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD admission not found"));

        if (!ipd.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        if (!"DISCHARGED".equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Admission must be DISCHARGED before it can be archived in MRD");
        }

        if (mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent()) {
            throw new IllegalArgumentException("Admission is already archived in MRD");
        }

        if (rackLocation == null || rackLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("Shelf/Rack location is required");
        }

        // Form 02 gate: the file must be completeness-checked before archival.
        // Incomplete files require an explicit, audited override reason.
        com.hms.dto.MrdCompletenessDTO completeness = computeCompleteness(ipdAdmissionId);
        if (!completeness.isComplete()) {
            if (overrideReason == null || overrideReason.trim().isEmpty()) {
                String failing = completeness.getItems().entrySet().stream()
                        .filter(e -> "FAIL".equals(e.getValue()))
                        .map(java.util.Map.Entry::getKey)
                        .reduce((a, b) -> a + ", " + b).orElse("");
                throw new IllegalArgumentException(
                        "File is incomplete (missing: " + failing + "). Provide an override reason to archive anyway.");
            }
            try {
                auditLogService.logAction(
                        "MRD_INCOMPLETE_OVERRIDE",
                        "Incomplete IPD file " + ipdAdmissionId + " archived with override: " + overrideReason.trim(),
                        securityHelper.getCurrentUserEmail(),
                        hospitalId,
                        "MRD",
                        String.valueOf(ipdAdmissionId),
                        overrideReason.trim());
            } catch (Exception ignored) {
                // audit failure must not block the archival itself
            }
        }

        // Generate sequential MRD number
        Integer maxSeq = mrdRecordRepository.findMaxMrdSequence();
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        String mrdNumber = "MRD-" + nextSeq;

        MrdRecord mrd = new MrdRecord();
        mrd.setHospitalId(hospitalId);
        mrd.setIpdAdmissionId(ipdAdmissionId);
        mrd.setMrdNumber(mrdNumber);
        mrd.setRackLocation(rackLocation.trim());
        mrd.setStatus("ARCHIVED");
        mrd.setArchivedAt(LocalDateTime.now());
        mrd.setArchivedById(userId);

        return mrdRecordRepository.save(mrd);
    }

    public void validateAdmissionActive(Long ipdAdmissionId) {
        if (ipdAdmissionId == null) return;

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD admission not found"));

        if ("DISCHARGED".equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalStateException("Clinical modifications are blocked: patient is already discharged.");
        }

        if (mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent()) {
            throw new IllegalStateException("Clinical record is locked and archived in MRD.");
        }
    }

    public boolean isAdmissionArchived(Long ipdAdmissionId) {
        if (ipdAdmissionId == null) return false;
        return mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent();
    }
}
