package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Blood Bank / BBTMS (Form 38 core). Implements the full safety chain:
 * donor -> unit (expiry-gated) -> request -> cross-match (BR-4 gate) -> issue -> transfusion
 * (BR-5/BR-6 reaction alarm). Every method is tenant-guarded.
 */
@Service
public class BloodBankService {

    private static final Logger log = LoggerFactory.getLogger(BloodBankService.class);

    private static final Set<String> VALID_COMPONENTS = Set.of("WHOLE_BLOOD", "PRBC", "FFP", "PLATELETS");
    private static final Set<String> VALID_REACTIONS = Set.of("NONE", "FEBRILE", "ALLERGIC", "HEMOLYTIC");

    @Autowired private BloodDonorRepository donorRepository;
    @Autowired private BloodUnitRepository unitRepository;
    @Autowired private BloodRequestRepository requestRepository;
    @Autowired private CrossMatchRepository crossMatchRepository;
    @Autowired private TransfusionRecordRepository transfusionRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    // ===== Donors =====

    @Transactional
    public BloodDonor registerDonor(BloodDonorRequest request) {
        Long hospitalId = requireHospital();
        if (request.getBloodGroup() == null || request.getRhType() == null) {
            throw new IllegalArgumentException("Blood group and Rh type are required");
        }
        BloodDonor donor = new BloodDonor();
        donor.setHospitalId(hospitalId);
        donor.setDonorNumber("DON-" + (donorRepository.countByHospitalId(hospitalId) + 1));
        donor.setName(request.getName());
        donor.setPhone(request.getPhone());
        donor.setBloodGroup(request.getBloodGroup().toUpperCase());
        donor.setRhType(request.getRhType().toUpperCase());
        donor.setStatus("ELIGIBLE");
        donor.setLastDonationDate(request.getLastDonationDate());
        BloodDonor saved = donorRepository.save(donor);
        audit("BLOOD_DONOR_REGISTERED", "Donor " + saved.getDonorNumber() + " registered", hospitalId);
        return saved;
    }

    public List<BloodDonor> getDonors() {
        return donorRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    // ===== Units =====

    /** BR-1/BR-2: adds a component bag traced to its donor. BR-3: auto-quarantines if already past expiry. */
    @Transactional
    public BloodUnit addUnit(BloodUnitRequest request) {
        Long hospitalId = requireHospital();
        if (!VALID_COMPONENTS.contains(String.valueOf(request.getComponentType()).toUpperCase())) {
            throw new IllegalArgumentException("Invalid component type: " + request.getComponentType());
        }
        BloodDonor donor = donorRepository.findByIdAndHospitalId(request.getDonorId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Donor not found"));
        if (request.getExpiryDate() == null) {
            throw new IllegalArgumentException("Expiry date is required");
        }

        BloodUnit unit = new BloodUnit();
        unit.setHospitalId(hospitalId);
        unit.setUnitNumber("BAG-" + hospitalId + "-" + System.currentTimeMillis());
        unit.setDonorId(donor.getId());
        unit.setComponentType(request.getComponentType().toUpperCase());
        unit.setBloodGroup(request.getBloodGroup().toUpperCase());
        unit.setRhType(request.getRhType().toUpperCase());
        unit.setHivResult(request.getHivResult());
        unit.setHbsagResult(request.getHbsagResult());
        unit.setMalariaResult(request.getMalariaResult());
        unit.setExpiryDate(request.getExpiryDate());
        // BR-3: reject/quarantine screening-reactive or already-expired units outright.
        boolean reactive = "REACTIVE".equalsIgnoreCase(request.getHivResult())
                || "REACTIVE".equalsIgnoreCase(request.getHbsagResult())
                || "REACTIVE".equalsIgnoreCase(request.getMalariaResult());
        if (reactive || !request.getExpiryDate().isAfter(LocalDate.now())) {
            unit.setStatus("QUARANTINED");
        } else {
            unit.setStatus("AVAILABLE");
        }
        BloodUnit saved = unitRepository.save(unit);
        audit("BLOOD_UNIT_ADDED", "Unit " + saved.getUnitNumber() + " added (" + saved.getStatus() + ")", hospitalId);
        return saved;
    }

    public List<BloodUnit> getAvailableUnits(String bloodGroup, String rhType) {
        Long hospitalId = requireHospital();
        expireStaleUnits(hospitalId);
        if (bloodGroup != null && rhType != null) {
            return unitRepository.findByHospitalIdAndBloodGroupAndRhTypeAndStatus(
                    hospitalId, bloodGroup.toUpperCase(), rhType.toUpperCase(), "AVAILABLE");
        }
        return unitRepository.findByHospitalIdAndStatusOrderByExpiryDateAsc(hospitalId, "AVAILABLE");
    }

    /** BR-3: sweeps AVAILABLE/RESERVED units whose expiry has passed into EXPIRED. */
    private void expireStaleUnits(Long hospitalId) {
        for (String status : List.of("AVAILABLE", "RESERVED")) {
            for (BloodUnit unit : unitRepository.findByHospitalIdAndStatusOrderByExpiryDateAsc(hospitalId, status)) {
                if (!unit.getExpiryDate().isAfter(LocalDate.now())) {
                    unit.setStatus("EXPIRED");
                    unitRepository.save(unit);
                }
            }
        }
    }

    // ===== Requests =====

    @Transactional
    public BloodRequest requestBlood(BloodRequestRequest request) {
        Long hospitalId = requireHospital();
        if (request.getUnitsRequested() == null || request.getUnitsRequested() <= 0) {
            throw new IllegalArgumentException("Units requested must be positive");
        }
        BloodRequest req = new BloodRequest();
        req.setHospitalId(hospitalId);
        req.setPatientId(request.getPatientId());
        req.setAdmissionId(request.getAdmissionId());
        req.setDepartment(request.getDepartment());
        req.setComponent(request.getComponent());
        req.setUnitsRequested(request.getUnitsRequested());
        req.setPriority(request.getPriority() != null ? request.getPriority().toUpperCase() : "ROUTINE");
        req.setStatus("PENDING");
        req.setRequestedByName(securityHelper.getCurrentUserEmail());
        BloodRequest saved = requestRepository.save(req);
        audit("BLOOD_REQUEST_CREATED", "Blood request for patient " + req.getPatientId() + " (" + req.getComponent() + ")", hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    public List<BloodRequest> getRequests() {
        return requestRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    // ===== Cross-match (BR-4) =====

    @Transactional
    public CrossMatch performCrossMatch(CrossMatchRequest request) {
        Long hospitalId = requireHospital();
        BloodRequest bloodRequest = requestRepository.findByIdAndHospitalId(request.getRequestId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Blood request not found"));
        BloodUnit unit = unitRepository.findByIdAndHospitalId(request.getBloodUnitId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Blood unit not found"));

        if (!"AVAILABLE".equalsIgnoreCase(unit.getStatus())) {
            throw new IllegalStateException("Cannot cross-match a unit that is not AVAILABLE (current: " + unit.getStatus() + ")");
        }
        String result = request.getResult() == null ? "" : request.getResult().toUpperCase();
        if (!result.equals("COMPATIBLE") && !result.equals("INCOMPATIBLE")) {
            throw new IllegalArgumentException("Cross-match result must be COMPATIBLE or INCOMPATIBLE");
        }

        CrossMatch xm = new CrossMatch();
        xm.setHospitalId(hospitalId);
        xm.setRequestId(bloodRequest.getId());
        xm.setBloodUnitId(unit.getId());
        xm.setPatientId(bloodRequest.getPatientId());
        xm.setResult(result);
        xm.setVerifiedByName(securityHelper.getCurrentUserEmail());
        xm.setVerifiedAt(LocalDateTime.now());
        CrossMatch saved = crossMatchRepository.save(xm);

        if ("COMPATIBLE".equals(result)) {
            unit.setStatus("RESERVED");
            unitRepository.save(unit);
            bloodRequest.setStatus("CROSS_MATCHING");
            requestRepository.save(bloodRequest);
        }

        audit("BLOOD_CROSS_MATCH", "Cross-match " + result + " for unit " + unit.getUnitNumber()
                + " / patient " + bloodRequest.getPatientId(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /**
     * BR-4: issues a reserved unit — requires a COMPATIBLE cross-match for this exact
     * unit+patient pairing. BR-3: re-validates the unit has not expired since reservation.
     */
    @Transactional
    public BloodUnit issueUnit(Long bloodUnitId, Long patientId) {
        Long hospitalId = requireHospital();
        BloodUnit unit = unitRepository.findByIdAndHospitalId(bloodUnitId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Blood unit not found"));

        if (!unit.getExpiryDate().isAfter(LocalDate.now())) {
            unit.setStatus("EXPIRED");
            unitRepository.save(unit);
            throw new IllegalStateException("Cannot issue unit " + unit.getUnitNumber() + ": it has expired.");
        }
        if ("QUARANTINED".equalsIgnoreCase(unit.getStatus()) || "EXPIRED".equalsIgnoreCase(unit.getStatus())
                || "ISSUED".equalsIgnoreCase(unit.getStatus())) {
            throw new IllegalStateException("Cannot issue unit " + unit.getUnitNumber() + " with status " + unit.getStatus());
        }

        CrossMatch xm = crossMatchRepository
                .findTopByHospitalIdAndBloodUnitIdAndPatientIdOrderByVerifiedAtDesc(hospitalId, bloodUnitId, patientId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot issue: no cross-match found for this unit and patient (BR-4)."));
        if (!"COMPATIBLE".equalsIgnoreCase(xm.getResult())) {
            throw new IllegalStateException("Cannot issue: the cross-match result is " + xm.getResult() + ", not COMPATIBLE (BR-4).");
        }

        unit.setStatus("ISSUED");
        BloodUnit saved = unitRepository.save(unit);

        audit("BLOOD_UNIT_ISSUED", "Unit " + unit.getUnitNumber() + " issued to patient " + patientId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    // ===== Transfusion (BR-5/BR-6) =====

    /** BR-5: starts the bedside transfusion record — one issued unit maps to exactly one patient. */
    @Transactional
    public TransfusionRecord startTransfusion(TransfusionRequest request) {
        Long hospitalId = requireHospital();
        BloodUnit unit = unitRepository.findByIdAndHospitalId(request.getBloodUnitId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Blood unit not found"));
        if (!"ISSUED".equalsIgnoreCase(unit.getStatus())) {
            throw new IllegalStateException("Cannot start transfusion: unit is not ISSUED (current: " + unit.getStatus() + ")");
        }
        if (transfusionRepository.findByHospitalIdAndBloodUnitId(hospitalId, unit.getId()).isPresent()) {
            throw new IllegalStateException("A transfusion record already exists for this unit.");
        }

        TransfusionRecord record = new TransfusionRecord();
        record.setHospitalId(hospitalId);
        record.setPatientId(request.getPatientId());
        record.setBloodUnitId(unit.getId());
        record.setStartedAt(LocalDateTime.now());
        record.setReaction("NONE");
        record.setNurseName(securityHelper.getCurrentUserEmail());
        TransfusionRecord saved = transfusionRepository.save(record);

        audit("BLOOD_TRANSFUSION_STARTED", "Transfusion started for unit " + unit.getUnitNumber()
                + " / patient " + request.getPatientId(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /**
     * BR-6: completing a transfusion with a reaction other than NONE freezes the patient's
     * other active transfusions and quarantines untouched units from the same donor.
     */
    @Transactional
    public TransfusionRecord completeTransfusion(Long recordId, TransfusionCompletionRequest request) {
        Long hospitalId = requireHospital();
        TransfusionRecord record = transfusionRepository.findById(recordId)
                .filter(r -> r.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new RuntimeException("Transfusion record not found"));
        if (record.getCompletedAt() != null) {
            throw new IllegalStateException("This transfusion has already been completed.");
        }
        String reaction = request.getReaction() == null ? "NONE" : request.getReaction().toUpperCase();
        if (!VALID_REACTIONS.contains(reaction)) {
            throw new IllegalArgumentException("Invalid reaction type: " + request.getReaction());
        }

        record.setCompletedAt(LocalDateTime.now());
        record.setReaction(reaction);
        record.setReactionNotes(request.getReactionNotes());
        TransfusionRecord saved = transfusionRepository.save(record);

        if (!"NONE".equals(reaction)) {
            // Freeze other active transfusions for this patient.
            List<TransfusionRecord> activeForPatient = transfusionRepository
                    .findByHospitalIdAndPatientIdAndCompletedAtIsNull(hospitalId, record.getPatientId());
            for (TransfusionRecord active : activeForPatient) {
                active.setReaction("FROZEN_PENDING_REVIEW");
                transfusionRepository.save(active);
            }

            // Quarantine sibling untouched units from the same donor.
            BloodUnit reactedUnit = unitRepository.findById(record.getBloodUnitId()).orElse(null);
            if (reactedUnit != null) {
                for (BloodUnit sibling : unitRepository.findByHospitalIdAndDonorId(hospitalId, reactedUnit.getDonorId())) {
                    if ("AVAILABLE".equalsIgnoreCase(sibling.getStatus()) || "RESERVED".equalsIgnoreCase(sibling.getStatus())) {
                        sibling.setStatus("QUARANTINED");
                        unitRepository.save(sibling);
                    }
                }
            }

            try {
                webSocketHandler.broadcast(hospitalId,
                        "{\"type\":\"TRANSFUSION_REACTION_ALERT\",\"patientId\":" + record.getPatientId()
                                + ",\"reaction\":\"" + reaction + "\"}");
            } catch (Exception e) {
                log.warn("Transfusion reaction alert broadcast failed: {}", e.getMessage());
            }
            audit("BLOOD_TRANSFUSION_REACTION", "Reaction " + reaction + " for patient " + record.getPatientId()
                    + " — active transfusions frozen, sibling units quarantined", hospitalId);
        } else {
            audit("BLOOD_TRANSFUSION_COMPLETED", "Transfusion completed for patient " + record.getPatientId(), hospitalId);
        }

        broadcast(hospitalId);
        return saved;
    }

    public List<TransfusionRecord> getPatientTransfusions(Long patientId) {
        return transfusionRepository.findByHospitalIdAndPatientIdOrderByStartedAtDesc(requireHospital(), patientId);
    }

    // ===== Helpers =====

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return hospitalId;
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
