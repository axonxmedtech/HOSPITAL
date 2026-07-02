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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Housekeeping & Facility Management (Form 37 core). Implements the bed/room turnover chain:
 * task creation -> housekeeper completion -> supervisor verification (BR-1, releases the
 * matching bed if one exists at that location), plus biomedical waste logging (BR-3) and
 * dual-sign-off facility complaints (BR-4). BR-7: every method is tenant-guarded.
 *
 * Scope note: the OT-readiness check-off integration (BR-2), linen ledger/shortage alerts
 * (BR-5), and pest-control compliance scheduling (BR-6) are deferred. The discharge flow in
 * IpdAdmissionService still sets a bed to "available" directly on discharge — this module
 * adds a second, supervisor-verified release path but does not modify that existing,
 * already-tested discharge code path, to avoid regressing a safety-critical flow outside
 * dedicated review.
 */
@Service
public class HousekeepingService {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingService.class);

    private static final Set<String> VALID_TASK_TYPES = Set.of("ROUTINE", "DEEP", "TERMINAL", "EMERGENCY");
    private static final Set<String> VALID_PRIORITIES = Set.of("ROUTINE", "URGENT");
    private static final Set<String> VALID_WASTE_TYPES = Set.of("YELLOW", "RED", "BLUE", "WHITE", "GENERAL");
    private static final Set<String> VALID_COMPLAINT_TYPES = Set.of("LEAKAGE", "LIGHTING", "ELECTRICAL", "AC", "PLUMBING");

    @Autowired private CleaningTaskRepository taskRepository;
    @Autowired private WasteCollectionRepository wasteRepository;
    @Autowired private FacilityComplaintRepository complaintRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional
    public CleaningTask createTask(CleaningTaskRequest request) {
        Long hospitalId = requireHospital();
        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        String taskType = request.getTaskType() == null ? "" : request.getTaskType().toUpperCase();
        if (!VALID_TASK_TYPES.contains(taskType)) {
            throw new IllegalArgumentException("Task type must be one of " + VALID_TASK_TYPES);
        }
        String priority = request.getPriority() == null ? "ROUTINE" : request.getPriority().toUpperCase();
        if (!VALID_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Priority must be ROUTINE or URGENT");
        }

        CleaningTask task = new CleaningTask();
        task.setHospitalId(hospitalId);
        task.setLocation(request.getLocation());
        task.setTaskType(taskType);
        task.setPriority(priority);
        task.setAssignedTo(request.getAssignedTo());
        task.setStatus("DIRTY");
        CleaningTask saved = taskRepository.save(task);
        audit("HOUSEKEEPING_TASK_CREATED", "Task for " + saved.getLocation() + " (" + taskType + ", " + priority + ")", hospitalId);
        return saved;
    }

    /** Housekeeper reports the task done; awaits supervisor verification (BR-1). */
    @Transactional
    public CleaningTask completeTask(Long taskId) {
        Long hospitalId = requireHospital();
        CleaningTask task = taskRepository.findByIdAndHospitalId(taskId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Cleaning task not found"));
        if ("PENDING_VERIFICATION".equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
            throw new IllegalStateException("Task has already been marked complete (status: " + task.getStatus() + ")");
        }
        if (task.getStartTime() == null) {
            task.setStartTime(LocalDateTime.now());
        }
        task.setCompletedAt(LocalDateTime.now());
        task.setStatus("PENDING_VERIFICATION");
        CleaningTask saved = taskRepository.save(task);
        audit("HOUSEKEEPING_TASK_COMPLETED", "Task for " + task.getLocation() + " marked complete, pending verification", hospitalId);
        return saved;
    }

    /**
     * BR-1: supervisor sign-off finalizes the task and releases the matching bed (if the
     * task location maps to a registered bed code) back to AVAILABLE.
     */
    @Transactional
    public CleaningTask verifyTask(Long taskId, TaskVerifyRequest request) {
        Long hospitalId = requireHospital();
        CleaningTask task = taskRepository.findByIdAndHospitalId(taskId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Cleaning task not found"));
        if (!"PENDING_VERIFICATION".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not pending verification (status: " + task.getStatus() + ")");
        }
        if (request.getSupervisorSig() == null || request.getSupervisorSig().isBlank()) {
            throw new IllegalArgumentException("Supervisor signature is required to release the location (BR-1)");
        }

        task.setStatus("COMPLETED");
        task.setSupervisorSig(request.getSupervisorSig());
        CleaningTask saved = taskRepository.save(task);

        bedRepository.findByHospitalIdAndBedCodeIgnoreCase(hospitalId, task.getLocation()).ifPresent(bed -> {
            bed.setStatus("available");
            bedRepository.save(bed);
        });

        audit("HOUSEKEEPING_TASK_VERIFIED", "Task for " + task.getLocation() + " verified complete — location released", hospitalId);
        return saved;
    }

    public List<CleaningTask> getTasks() {
        return taskRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    /** BR-3: logs a color-coded biomedical waste collection with weight and barcode tag. */
    @Transactional
    public WasteCollection logWaste(WasteCollectionRequest request) {
        Long hospitalId = requireHospital();
        String wasteType = request.getWasteType() == null ? "" : request.getWasteType().toUpperCase();
        if (!VALID_WASTE_TYPES.contains(wasteType)) {
            throw new IllegalArgumentException("Waste type must be one of " + VALID_WASTE_TYPES);
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Waste weight must be positive");
        }
        if (request.getBarcodeTag() == null || request.getBarcodeTag().isBlank()) {
            throw new IllegalArgumentException("Barcode tag is required");
        }
        if (request.getVendor() == null || request.getVendor().isBlank()) {
            throw new IllegalArgumentException("Disposal vendor is required");
        }

        WasteCollection waste = new WasteCollection();
        waste.setHospitalId(hospitalId);
        waste.setWasteType(wasteType);
        waste.setQuantity(request.getQuantity());
        waste.setBarcodeTag(request.getBarcodeTag());
        waste.setCollectorName(securityHelper.getCurrentUserEmail());
        waste.setVendor(request.getVendor());
        waste.setManifestNumber(request.getManifestNumber());
        waste.setCollectionTime(LocalDateTime.now());
        WasteCollection saved = wasteRepository.save(waste);

        audit("HOUSEKEEPING_WASTE_LOGGED", wasteType + " waste " + saved.getQuantity() + "kg logged (tag " + saved.getBarcodeTag() + ")", hospitalId);
        return saved;
    }

    public List<WasteCollection> getWasteLog() {
        return wasteRepository.findByHospitalIdOrderByCollectionTimeDesc(requireHospital());
    }

    @Transactional
    public FacilityComplaint openComplaint(FacilityComplaintRequest request) {
        Long hospitalId = requireHospital();
        if (request.getLocation() == null || request.getLocation().isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        String complaintType = request.getComplaintType() == null ? "" : request.getComplaintType().toUpperCase();
        if (!VALID_COMPLAINT_TYPES.contains(complaintType)) {
            throw new IllegalArgumentException("Complaint type must be one of " + VALID_COMPLAINT_TYPES);
        }

        FacilityComplaint complaint = new FacilityComplaint();
        complaint.setHospitalId(hospitalId);
        complaint.setLocation(request.getLocation());
        complaint.setComplaintType(complaintType);
        complaint.setReportedBy(securityHelper.getCurrentUserEmail());
        complaint.setStatus("OPEN");
        FacilityComplaint saved = complaintRepository.save(complaint);
        audit("HOUSEKEEPING_COMPLAINT_OPENED", complaintType + " issue reported at " + saved.getLocation(), hospitalId);
        return saved;
    }

    /**
     * BR-4: the complaint only reaches CLOSED once both the resolving engineer and the
     * reporting nurse have confirmed — regardless of confirmation order.
     */
    @Transactional
    public FacilityComplaint confirmComplaint(Long complaintId, ComplaintConfirmRequest request) {
        Long hospitalId = requireHospital();
        FacilityComplaint complaint = complaintRepository.findByIdAndHospitalId(complaintId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        if ("CLOSED".equals(complaint.getStatus())) {
            throw new IllegalStateException("This complaint has already been closed.");
        }
        String role = request.getRole() == null ? "" : request.getRole().toUpperCase();
        if (!role.equals("ENGINEER") && !role.equals("NURSE")) {
            throw new IllegalArgumentException("Role must be ENGINEER or NURSE");
        }

        if ("ENGINEER".equals(role)) {
            complaint.setEngineerConfirmed(true);
            if (request.getResolution() != null && !request.getResolution().isBlank()) {
                complaint.setResolution(request.getResolution());
            }
        } else {
            complaint.setNurseConfirmed(true);
        }

        if (complaint.isEngineerConfirmed() && complaint.isNurseConfirmed()) {
            complaint.setStatus("CLOSED");
            complaint.setResolvedAt(LocalDateTime.now());
        } else {
            complaint.setStatus("IN_PROGRESS");
        }
        FacilityComplaint saved = complaintRepository.save(complaint);

        audit("HOUSEKEEPING_COMPLAINT_CONFIRMED", role + " confirmed complaint #" + complaint.getId()
                + " (status: " + saved.getStatus() + ")", hospitalId);
        return saved;
    }

    public List<FacilityComplaint> getComplaints() {
        return complaintRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
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
