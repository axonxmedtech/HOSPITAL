package com.hms.service.hospital;

import com.hms.dto.VitalsRequest;
import com.hms.entity.IpdAdmission;
import com.hms.entity.VitalsRecord;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.VitalsRecordRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * VitalsService - record and read IPD vitals (Phase 1 Nurse module).
 * Writes are nurse-only and require an active assignment; reads are also open
 * to doctors/admins (hospital-scoped). Edits are limited to the author within a
 * short window.
 */
@Service
public class VitalsService {

    private static final Logger logger = LoggerFactory.getLogger(VitalsService.class);
    private static final Duration EDIT_WINDOW = Duration.ofHours(12);

    @Autowired private VitalsRecordRepository vitalsRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private NurseAccessGuard nurseAccessGuard;
    @Autowired private com.hms.security.NurseWriteAccess nurseWriteAccess;
    @Autowired private com.hms.security.PerformingNurseResolver performingNurseResolver;
    @Autowired private AuditLogService auditLogService;
    @Autowired private FormAccessService formAccessService;
    @Autowired private com.hms.service.RealtimeNotifier notifier;

    @Transactional
    public VitalsRecord create(VitalsRequest req) {
        Long hospitalId = requireHospitalId();
        formAccessService.assertCanEdit("VITALS");
        if (req.getIpdAdmissionId() == null) {
            throw new IllegalArgumentException("ipdAdmissionId is required");
        }
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);
        nurseWriteAccess.assertCanWriteFor(admission.getId());
        validate(req);

        VitalsRecord v = new VitalsRecord();
        v.setHospitalId(hospitalId);
        v.setIpdAdmissionId(admission.getId());
        v.setPatientId(admission.getPatientId());
        v.setRecordedByUserId(securityHelper.getCurrentUserId());
        v.setRecordedAt(req.getRecordedAt() != null ? req.getRecordedAt() : LocalDateTime.now());
        applyMeasurements(v, req);
        v.setPerformedByNurseId(performingNurseResolver.resolve(req.getPerformedByNurseId()));
        v.setIsActive(true);
        VitalsRecord saved = vitalsRepository.save(v);

        audit("VITALS_RECORDED", "Recorded vitals for IPD admission " + admission.getIpdNumber(),
                hospitalId, admission.getId());
        // The doctor may have this same IPD case open while the nurse records these — push it so
        // the vitals appear on their screen instead of waiting for a manual refresh.
        notifier.refresh(hospitalId);
        return saved;
    }

    public List<VitalsRecord> getByAdmission(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        // Nurses may only read admissions they are assigned to; doctors/admins may
        // read any admission in their hospital.
        if ("NURSE".equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }
        return vitalsRepository.findByIpdAdmissionIdAndIsActiveTrueOrderByRecordedAtDesc(ipdAdmissionId);
    }

    @Transactional
    public VitalsRecord update(String publicId, VitalsRequest req) {
        Long hospitalId = requireHospitalId();
        formAccessService.assertCanEdit("VITALS");
        VitalsRecord v = vitalsRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Vitals record not found"));
        if (!hospitalId.equals(v.getHospitalId())) {
            throw new UnauthorizedException("Access denied: record belongs to another hospital");
        }
        if (!v.getRecordedByUserId().equals(securityHelper.getCurrentUserId())) {
            throw new AccessDeniedException("Only the nurse who recorded these vitals can edit them");
        }
        if (Duration.between(v.getCreatedAt(), LocalDateTime.now()).compareTo(EDIT_WINDOW) > 0) {
            throw new IllegalArgumentException("Edit window has passed for this vitals record");
        }
        validate(req);
        applyMeasurements(v, req);
        if (req.getRecordedAt() != null) v.setRecordedAt(req.getRecordedAt());
        VitalsRecord saved = vitalsRepository.save(v);
        audit("VITALS_UPDATED", "Updated vitals record " + v.getPublicId(), hospitalId, v.getIpdAdmissionId());
        notifier.refresh(hospitalId);
        return saved;
    }

    // --- helpers ---

    private void applyMeasurements(VitalsRecord v, VitalsRequest req) {
        v.setTemperature(req.getTemperature());
        v.setPulse(req.getPulse());
        v.setBpSystolic(req.getBpSystolic());
        v.setBpDiastolic(req.getBpDiastolic());
        v.setRespiratoryRate(req.getRespiratoryRate());
        v.setSpo2(req.getSpo2());
        v.setWeight(req.getWeight());
        v.setPainScore(req.getPainScore());
        v.setRemarks(req.getRemarks());
    }

    private void validate(VitalsRequest req) {
        boolean any = req.getTemperature() != null || req.getPulse() != null || req.getBpSystolic() != null
                || req.getBpDiastolic() != null || req.getRespiratoryRate() != null || req.getSpo2() != null
                || req.getWeight() != null || req.getPainScore() != null;
        if (!any) {
            throw new IllegalArgumentException("At least one vital measurement is required");
        }
        if (req.getRecordedAt() != null && req.getRecordedAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("Recorded time cannot be in the future");
        }
        // No upper bounds on vitals — only reject negatives. A value can be anything from 0 up.
        nonNegative("Temperature (°F)", req.getTemperature());
        nonNegative("Pulse", req.getPulse());
        nonNegative("Systolic BP", req.getBpSystolic());
        nonNegative("Diastolic BP", req.getBpDiastolic());
        nonNegative("Respiratory rate", req.getRespiratoryRate());
        nonNegative("SpO2", req.getSpo2());
        nonNegative("Pain score", req.getPainScore());
        nonNegative("Weight", req.getWeight());
    }

    private void nonNegative(String name, Integer value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private void nonNegative(String name, BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private IpdAdmission requireAdmission(Long ipdAdmissionId, Long hospitalId) {
        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        if (!hospitalId.equals(admission.getHospitalId())) {
            throw new UnauthorizedException("Access denied: admission belongs to another hospital");
        }
        return admission;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return hospitalId;
    }

    private void audit(String action, String details, Long hospitalId, Long admissionId) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), hospitalId,
                    "IPD", admissionId != null ? admissionId.toString() : null, null);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for {}", action, e);
        }
    }
}
