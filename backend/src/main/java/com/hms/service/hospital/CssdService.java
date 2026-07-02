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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * CSSD sterile instrument lifecycle (Form 35 core). Implements the safety chain:
 * return -> autoclave cycle -> supervisor verification (BR-2 quarantine gate) -> issue
 * (BR-1 sterility lock, BR-3 expiry gate). BR-5: every transition is audit-ledgered.
 * BR-7: every method is tenant-guarded.
 *
 * Scope note: individual per-instrument tracking (cssd_instrument), formal incident-report
 * entities (BR-4), and sterilizer machine-lock scheduling (BR-6) are deferred — this covers
 * the indivisible tray-level sterility safety chain.
 */
@Service
public class CssdService {

    private static final Logger log = LoggerFactory.getLogger(CssdService.class);

    private static final Set<String> VALID_METHODS = Set.of("STEAM", "ETO", "PLASMA", "DRY_HEAT");
    private static final Set<String> VALID_RETURN_CONDITIONS = Set.of("DIRTY", "DAMAGED", "MISSING");
    private static final int TRAY_STERILITY_DAYS = 30;

    @Autowired private CssdTrayRepository trayRepository;
    @Autowired private SterilizationCycleRepository cycleRepository;
    @Autowired private CssdIssueRepository issueRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional
    public CssdTray registerTray(CssdTrayRegisterRequest request) {
        Long hospitalId = requireHospital();
        if (request.getTrayName() == null || request.getTrayName().isBlank()) {
            throw new IllegalArgumentException("Tray name is required");
        }
        if (request.getBarcode() == null || request.getBarcode().isBlank()) {
            throw new IllegalArgumentException("Barcode is required");
        }
        if (trayRepository.findByHospitalIdAndBarcode(hospitalId, request.getBarcode()).isPresent()) {
            throw new IllegalStateException("A tray with this barcode already exists");
        }
        CssdTray tray = new CssdTray();
        tray.setHospitalId(hospitalId);
        tray.setTrayName(request.getTrayName());
        tray.setSpecialty(request.getSpecialty());
        tray.setBarcode(request.getBarcode());
        tray.setStatus("DIRTY");
        CssdTray saved = trayRepository.save(tray);
        audit("CSSD_TRAY_REGISTERED", "Tray " + saved.getBarcode() + " registered", hospitalId);
        return saved;
    }

    public List<CssdTray> getTrays() {
        return trayRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    /** BR-4 (partial): logs an audit entry noting count/damage discrepancies on return. */
    @Transactional
    public CssdTray returnTray(CssdReturnRequest request) {
        Long hospitalId = requireHospital();
        CssdTray tray = findTray(hospitalId, request.getTrayBarcode());
        String condition = request.getCondition() == null ? "" : request.getCondition().toUpperCase();
        if (!VALID_RETURN_CONDITIONS.contains(condition)) {
            throw new IllegalArgumentException("Return condition must be DIRTY, DAMAGED, or MISSING");
        }
        tray.setStatus("DIRTY");
        tray.setCycleId(null);
        tray.setExpiryDate(null);
        CssdTray saved = trayRepository.save(tray);

        String detail = "Tray " + tray.getBarcode() + " returned from " + request.getFromDepartment()
                + " (" + condition + ")";
        if (!"DIRTY".equals(condition)) {
            audit("CSSD_RETURN_DISCREPANCY", detail, hospitalId);
        } else {
            audit("CSSD_TRAY_RETURNED", detail, hospitalId);
        }
        return saved;
    }

    /** Loads the selected trays into an autoclave run. */
    @Transactional
    public SterilizationCycle startCycle(CssdCycleStartRequest request) {
        Long hospitalId = requireHospital();
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        if (!VALID_METHODS.contains(method)) {
            throw new IllegalArgumentException("Sterilize method must be one of " + VALID_METHODS);
        }
        if (request.getTemperature() == null || request.getPressure() == null || request.getDuration() == null) {
            throw new IllegalArgumentException("Temperature, pressure, and duration are required");
        }
        if (request.getTrayIds() == null || request.getTrayIds().isEmpty()) {
            throw new IllegalArgumentException("At least one tray must be selected");
        }

        List<CssdTray> trays = request.getTrayIds().stream()
                .map(id -> findTray(hospitalId, id))
                .toList();
        for (CssdTray tray : trays) {
            if (!"DIRTY".equals(tray.getStatus())) {
                throw new IllegalStateException("Tray " + tray.getBarcode()
                        + " cannot be loaded (current status: " + tray.getStatus() + ")");
            }
        }

        SterilizationCycle cycle = new SterilizationCycle();
        cycle.setHospitalId(hospitalId);
        cycle.setCycleNumber(request.getMachineId() + "-" + (cycleRepository.countByHospitalId(hospitalId) + 1));
        cycle.setMachineId(request.getMachineId());
        cycle.setMethod(method);
        cycle.setTemperature(request.getTemperature());
        cycle.setPressure(request.getPressure());
        cycle.setDuration(request.getDuration());
        cycle.setStatus("IN_PROGRESS");
        SterilizationCycle saved = cycleRepository.save(cycle);

        for (CssdTray tray : trays) {
            tray.setStatus("IN_STERILIZER");
            tray.setCycleId(saved.getId());
            trayRepository.save(tray);
        }

        audit("CSSD_CYCLE_STARTED", "Cycle " + saved.getCycleNumber() + " started on " + saved.getMachineId()
                + " with " + trays.size() + " tray(s)", hospitalId);
        return saved;
    }

    /**
     * BR-2: chemical or biological FAIL quarantines every tray in the load and blocks dispatch.
     * A PASS releases the load: trays become STERILE with a computed sterility expiry.
     */
    @Transactional
    public SterilizationCycle verifyCycle(Long cycleId, CssdCycleVerifyRequest request) {
        Long hospitalId = requireHospital();
        SterilizationCycle cycle = cycleRepository.findByIdAndHospitalId(cycleId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Sterilization cycle not found"));
        if (!"IN_PROGRESS".equals(cycle.getStatus())) {
            throw new IllegalStateException("Cycle has already been verified (status: " + cycle.getStatus() + ")");
        }
        String chemical = normalizeResult(request.getChemicalResult(), "Chemical indicator");
        String biological = normalizeResult(request.getBiologicalResult(), "Biological indicator");

        cycle.setChemicalResult(chemical);
        cycle.setBiologicalResult(biological);
        cycle.setApprovedBy(securityHelper.getCurrentUserId());
        cycle.setApprovedBySig(request.getApprovedBySig());

        List<CssdTray> trays = trayRepository.findByHospitalIdAndCycleId(hospitalId, cycle.getId());
        boolean failed = "FAIL".equals(chemical) || "FAIL".equals(biological);

        if (failed) {
            cycle.setStatus("FAILED");
            for (CssdTray tray : trays) {
                tray.setStatus("QUARANTINED");
                trayRepository.save(tray);
            }
            audit("CSSD_CYCLE_QUARANTINED", "Cycle " + cycle.getCycleNumber()
                    + " failed indicators — " + trays.size() + " tray(s) quarantined", hospitalId);
        } else {
            cycle.setStatus("PASSED");
            LocalDate expiry = LocalDate.now().plusDays(TRAY_STERILITY_DAYS);
            for (CssdTray tray : trays) {
                tray.setStatus("STERILE");
                tray.setExpiryDate(expiry);
                trayRepository.save(tray);
            }
            audit("CSSD_CYCLE_VERIFIED", "Cycle " + cycle.getCycleNumber()
                    + " passed — " + trays.size() + " tray(s) released sterile (expiry " + expiry + ")", hospitalId);
        }

        return cycleRepository.save(cycle);
    }

    /**
     * BR-1: only STERILE trays may be issued. BR-3: expired trays are blocked and routed
     * back to the cleaning queue instead of being dispatched.
     */
    @Transactional
    public CssdIssue issueTray(CssdIssueRequest request) {
        Long hospitalId = requireHospital();
        CssdTray tray = findTray(hospitalId, request.getTrayBarcode());

        if (tray.getExpiryDate() != null && tray.getExpiryDate().isBefore(LocalDate.now())
                && "STERILE".equals(tray.getStatus())) {
            tray.setStatus("DIRTY");
            tray.setCycleId(null);
            tray.setExpiryDate(null);
            trayRepository.save(tray);
            audit("CSSD_TRAY_EXPIRED", "Tray " + tray.getBarcode() + " expired at checkout — routed back to cleaning queue", hospitalId);
            throw new IllegalStateException("Tray " + tray.getBarcode()
                    + " has passed its sterility expiry and has been routed back for reprocessing.");
        }
        if (!"STERILE".equals(tray.getStatus())) {
            throw new IllegalStateException("Tray " + tray.getBarcode()
                    + " is not sterile and cannot be issued (current status: " + tray.getStatus() + ").");
        }

        CssdIssue issue = new CssdIssue();
        issue.setHospitalId(hospitalId);
        issue.setTrayId(tray.getId());
        issue.setIssuedToDepartment(request.getIssuedToDepartment());
        issue.setIssuedByName(securityHelper.getCurrentUserEmail());
        issue.setReceivedBy(request.getReceivedBy());
        issue.setIssueTime(LocalDateTime.now());
        CssdIssue saved = issueRepository.save(issue);

        tray.setStatus("ISSUED");
        trayRepository.save(tray);

        audit("CSSD_TRAY_ISSUED", "Tray " + tray.getBarcode() + " issued to " + request.getIssuedToDepartment(), hospitalId);
        return saved;
    }

    public List<SterilizationCycle> getCycles() {
        return cycleRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    public List<CssdIssue> getIssues() {
        return issueRepository.findByHospitalIdOrderByIssueTimeDesc(requireHospital());
    }

    // ===== Helpers =====

    private CssdTray findTray(Long hospitalId, String barcode) {
        return trayRepository.findByHospitalIdAndBarcode(hospitalId, barcode)
                .orElseThrow(() -> new RuntimeException("Tray not found: " + barcode));
    }

    private CssdTray findTray(Long hospitalId, Long trayId) {
        return trayRepository.findByIdAndHospitalId(trayId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Tray not found: " + trayId));
    }

    private String normalizeResult(String value, String label) {
        String result = value == null ? "" : value.toUpperCase();
        if (!result.equals("PASS") && !result.equals("FAIL")) {
            throw new IllegalArgumentException(label + " result must be PASS or FAIL");
        }
        return result;
    }

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
