package com.hms.service.hospital;

import com.hms.exception.ResourceNotFoundException;

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
    /** ICU Phase 4: read-only. ICU-4 records observations; it never touches the stay lifecycle. */
    @Autowired private com.hms.repository.IcuStayRepository icuStayRepository;

    @Autowired private com.hms.service.hospital.icu.IcuAlertEvaluator alertEvaluator;

    @Transactional
    public VitalsRecord create(VitalsRequest req) {
        Long hospitalId = requireHospitalId();
        formAccessService.assertCanEdit("VITALS");
        if (req.getIpdAdmissionId() == null) {
            throw new IllegalArgumentException("ipdAdmissionId is required");
        }
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);
        com.hms.service.hospital.IpdClinicalState.requireActive(admission);
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
        // ICU-9: compare against this hospital's configured thresholds. Fail-safe inside, and
        // the ICU-stay check is a supplier so it only runs when a threshold actually exists.
        alertEvaluator.evaluateVitals(saved, admission, () -> fallsInsideIcuStay(saved));
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
                .orElseThrow(() -> new ResourceNotFoundException("Vitals record not found"));
        if (!hospitalId.equals(v.getHospitalId())) {
            throw new UnauthorizedException("Access denied: record belongs to another hospital");
        }
        if (!v.getRecordedByUserId().equals(securityHelper.getCurrentUserId())) {
            throw new AccessDeniedException("Only the nurse who recorded these vitals can edit them");
        }
        if (Duration.between(v.getCreatedAt().atZone(java.time.ZoneId.systemDefault()),
                java.time.ZonedDateTime.now()).compareTo(EDIT_WINDOW) > 0) {
            throw new IllegalArgumentException("Edit window has passed for this vitals record");
        }
        // ICU Phase 4: an observation recorded during an ICU stay is never edited in place.
        // Correct it instead -- that keeps the original readable, which is the whole point:
        // in critical care the earlier value is itself evidence. Rows OUTSIDE every ICU window
        // are untouched by this and behave exactly as they did before.
        if (fallsInsideIcuStay(v)) {
            throw new com.hms.exception.ConflictException(
                    "This observation was recorded during an ICU stay and cannot be overwritten. "
                            + "Record a correction instead, so the original value is preserved.");
        }

        validate(req);
        applyMeasurements(v, req);
        if (req.getRecordedAt() != null) v.setRecordedAt(req.getRecordedAt());
        VitalsRecord saved = vitalsRepository.save(v);
        audit("VITALS_UPDATED", "Updated vitals record " + v.getPublicId(), hospitalId, v.getIpdAdmissionId());
        notifier.refresh(hospitalId);
        return saved;
    }

    /**
     * Corrects an ICU-period observation by writing a NEW row that supersedes the original.
     *
     * <p>The original is neither modified nor deactivated: it stays in the history, rendered as
     * superseded. Hiding it would recreate exactly the loss this path exists to prevent.
     *
     * <p><b>Authorisation is deliberately identical to {@link #update}</b> -- the same VITALS
     * form gate, the same "only the nurse who recorded it", the same edit window. ICU-4's
     * improvement is history preservation, NOT broader access: nothing here lets a user change
     * an observation they could not already have changed.
     */
    @Transactional
    public VitalsRecord correct(String publicId, VitalsRequest req) {
        Long hospitalId = requireHospitalId();
        formAccessService.assertCanEdit("VITALS");

        VitalsRecord original = vitalsRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Vitals record not found"));

        assertMayAmend(original);

        if (!fallsInsideIcuStay(original)) {
            throw new IllegalArgumentException(
                    "This observation was not recorded during an ICU stay. Use the normal edit instead.");
        }

        validate(req);

        VitalsRecord correction = new VitalsRecord();
        correction.setHospitalId(hospitalId);
        correction.setIpdAdmissionId(original.getIpdAdmissionId());
        correction.setPatientId(original.getPatientId());
        correction.setRecordedByUserId(securityHelper.getCurrentUserId());
        // The correction describes the SAME moment of observation, so it keeps the original's
        // recorded_at unless the caller is explicitly correcting the time itself.
        correction.setRecordedAt(req.getRecordedAt() != null ? req.getRecordedAt() : original.getRecordedAt());
        applyMeasurements(correction, req);
        correction.setPerformedByNurseId(performingNurseResolver.resolve(req.getPerformedByNurseId()));
        correction.setSupersedesVitalsId(original.getId());
        correction.setIsActive(true);
        VitalsRecord saved = vitalsRepository.save(correction);

        audit("VITALS_CORRECTED",
                "Corrected vitals record " + original.getPublicId() + " with " + saved.getPublicId(),
                hospitalId, original.getIpdAdmissionId());
        // A correction states what the value actually was, so it is evaluated like any other
        // observation. correct() already refused anything outside an ICU stay.
        alertEvaluator.evaluateVitals(saved,
                requireAdmission(saved.getIpdAdmissionId(), hospitalId), () -> true);
        notifier.refresh(hospitalId);
        return saved;
    }

    /**
     * The existing amendment rules, unchanged and now shared by update() and correct(): only the
     * nurse who recorded the observation, and only inside the edit window.
     */
    private void assertMayAmend(VitalsRecord v) {
        if (!v.getRecordedByUserId().equals(securityHelper.getCurrentUserId())) {
            throw new AccessDeniedException("Only the nurse who recorded these vitals can edit them");
        }
        if (Duration.between(v.getCreatedAt().atZone(java.time.ZoneId.systemDefault()),
                java.time.ZonedDateTime.now()).compareTo(EDIT_WINDOW) > 0) {
            throw new IllegalArgumentException("Edit window has passed for this vitals record");
        }
    }

    /** Whether this observation was taken while the patient was in critical care (ICU-3 data). */
    private boolean fallsInsideIcuStay(VitalsRecord v) {
        if (v.getRecordedAt() == null || v.getIpdAdmissionId() == null) return false;
        return icuStayRepository.existsCoveringInstant(
                v.getIpdAdmissionId(), v.getHospitalId(), v.getRecordedAt());
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
        // ICU Phase 4. Null on a ward reading, so a general-ward row is byte-identical to before.
        v.setMapMmhg(req.getMapMmhg());
        v.setCvpCmh2o(req.getCvpCmh2o());
        v.setUrineOutputMl(req.getUrineOutputMl());
        v.setGcsEye(req.getGcsEye());
        v.setGcsVerbal(req.getGcsVerbal());
        v.setGcsMotor(req.getGcsMotor());
        v.setGcsTotal(gcsTotalOf(req));
    }

    /**
     * E+V+M, or null when no component was given. Arithmetic, not interpretation: ICU-4 records
     * what was observed and derives no severity, risk or recommendation from it.
     */
    private Integer gcsTotalOf(VitalsRequest req) {
        if (req.getGcsEye() == null && req.getGcsVerbal() == null && req.getGcsMotor() == null) {
            return null;
        }
        return (req.getGcsEye() == null ? 0 : req.getGcsEye())
                + (req.getGcsVerbal() == null ? 0 : req.getGcsVerbal())
                + (req.getGcsMotor() == null ? 0 : req.getGcsMotor());
    }

    private void validate(VitalsRequest req) {
        // ICU-4: the ICU observations count as measurements too. Omitting them here meant an ICU
        // reading of only MAP, CVP, urine output or GCS was rejected as "empty" -- the exact
        // values this phase was added to capture.
        boolean any = req.getTemperature() != null || req.getPulse() != null || req.getBpSystolic() != null
                || req.getBpDiastolic() != null || req.getRespiratoryRate() != null || req.getSpo2() != null
                || req.getWeight() != null || req.getPainScore() != null
                || req.getMapMmhg() != null || req.getCvpCmh2o() != null || req.getUrineOutputMl() != null
                || req.getGcsEye() != null || req.getGcsVerbal() != null || req.getGcsMotor() != null;
        if (!any) {
            throw new IllegalArgumentException("At least one vital measurement is required");
        }
        if (req.getRecordedAt() != null && req.getRecordedAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("Recorded time cannot be in the future");
        }
        // Plausibility limits, not clinical thresholds: anything inside a range is accepted however
        // abnormal it is; values outside are typing slips (pulse 900, 37 typed into a °F field)
        // that would otherwise sit in the chart and on the ICU board as if they were real.
        range("Temperature (°F)", req.getTemperature(), 75, 115,
                "Temperature is recorded in °F (normal is about 98.6)");
        range("Pulse", req.getPulse(), 0, 300, null);
        range("Systolic BP", req.getBpSystolic(), 0, 300, null);
        range("Diastolic BP", req.getBpDiastolic(), 0, 250, null);
        if (req.getBpSystolic() != null && req.getBpDiastolic() != null
                && req.getBpSystolic() > 0 && req.getBpDiastolic() >= req.getBpSystolic()) {
            throw new IllegalArgumentException("Diastolic BP must be lower than systolic BP");
        }
        range("Respiratory rate", req.getRespiratoryRate(), 0, 100, null);
        range("SpO2", req.getSpo2(), 0, 100, null);
        range("Pain score", req.getPainScore(), 0, 10, null);
        range("Weight (kg)", req.getWeight(), 0, 400, null);
        range("MAP", req.getMapMmhg(), 0, 250, null);
        range("CVP", req.getCvpCmh2o(), -10, 60, null);
        range("Urine output (ml)", req.getUrineOutputMl(), 0, 10000, null);
        range("GCS eye", req.getGcsEye(), 1, 4, null);
        range("GCS verbal", req.getGcsVerbal(), 1, 5, null);
        range("GCS motor", req.getGcsMotor(), 1, 6, null);
    }

    private void range(String name, Integer value, int min, int max, String hint) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max
                    + (hint == null ? "" : ". " + hint));
        }
    }

    private void range(String name, BigDecimal value, int min, int max, String hint) {
        if (value != null && (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max
                    + (hint == null ? "" : ". " + hint));
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
