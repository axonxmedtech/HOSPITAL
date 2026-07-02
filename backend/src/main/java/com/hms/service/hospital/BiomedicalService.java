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
 * Biomedical Engineering & Medical Equipment Management (Form 36 core). Implements the asset
 * safety chain: register (BR-1 unique code) -> breakdown ticket (device flagged DOWN) ->
 * calibration certification (BR-2 overdue lock) -> department-head-confirmed close (BR-4).
 * BR-6: equipment is never deleted, only ever moves between statuses. BR-7: tenant-guarded.
 *
 * Scope note: preventive-maintenance scheduling (`maintenance_schedule`) and the spare-parts
 * inventory deduction (BR-3) are deferred — this covers the equipment-availability safety
 * chain that gates clinical usage.
 */
@Service
public class BiomedicalService {

    private static final Logger log = LoggerFactory.getLogger(BiomedicalService.class);

    private static final Set<String> VALID_PRIORITIES = Set.of("LOW", "MEDIUM", "CRITICAL");
    private static final Set<String> VALID_RESULTS = Set.of("PASS", "FAIL");

    @Autowired private MedicalEquipmentRepository equipmentRepository;
    @Autowired private BreakdownTicketRepository ticketRepository;
    @Autowired private CalibrationRecordRepository calibrationRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional
    public MedicalEquipment registerEquipment(EquipmentRegisterRequest request) {
        Long hospitalId = requireHospital();
        if (request.getEquipmentName() == null || request.getEquipmentName().isBlank()) {
            throw new IllegalArgumentException("Equipment name is required");
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (request.getSerialNumber() == null || request.getSerialNumber().isBlank()) {
            throw new IllegalArgumentException("Serial number is required");
        }
        if (request.getDepartment() == null || request.getDepartment().isBlank()) {
            throw new IllegalArgumentException("Department is required");
        }

        MedicalEquipment equipment = new MedicalEquipment();
        equipment.setHospitalId(hospitalId);
        equipment.setAssetCode("EQ-" + hospitalId + "-" + (equipmentRepository.countByHospitalId(hospitalId) + 1));
        equipment.setEquipmentName(request.getEquipmentName());
        equipment.setCategory(request.getCategory());
        equipment.setManufacturer(request.getManufacturer());
        equipment.setModel(request.getModel());
        equipment.setSerialNumber(request.getSerialNumber());
        equipment.setDepartment(request.getDepartment());
        equipment.setLocation(request.getLocation());
        equipment.setWarrantyExpiry(request.getWarrantyExpiry());
        equipment.setStatus("ACTIVE");
        MedicalEquipment saved = equipmentRepository.save(equipment);
        audit("BIOMEDICAL_EQUIPMENT_REGISTERED", "Asset " + saved.getAssetCode() + " (" + saved.getEquipmentName() + ") registered", hospitalId);
        return saved;
    }

    /** BR-2: sweeps ACTIVE assets whose latest calibration has passed its due date. */
    public List<MedicalEquipment> getEquipment() {
        Long hospitalId = requireHospital();
        List<MedicalEquipment> equipment = equipmentRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        for (MedicalEquipment item : equipment) {
            if (!"ACTIVE".equals(item.getStatus())) continue;
            calibrationRepository.findTopByHospitalIdAndEquipmentIdOrderByCalibrationDateDesc(hospitalId, item.getId())
                    .filter(record -> record.getDueDate().isBefore(LocalDate.now()))
                    .ifPresent(record -> {
                        item.setStatus("CALIBRATION_OVERDUE");
                        equipmentRepository.save(item);
                    });
        }
        return equipment;
    }

    /** Files a breakdown/repair request; flags the device DOWN until closed (BR-4). */
    @Transactional
    public BreakdownTicket openBreakdownTicket(BreakdownTicketRequest request) {
        Long hospitalId = requireHospital();
        MedicalEquipment equipment = equipmentRepository.findByIdAndHospitalId(request.getEquipmentId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Equipment not found"));
        if ("RETIRED".equals(equipment.getStatus())) {
            throw new IllegalStateException("Cannot open a ticket against retired equipment");
        }
        String priority = request.getPriority() == null ? "" : request.getPriority().toUpperCase();
        if (!VALID_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Priority must be LOW, MEDIUM, or CRITICAL");
        }
        if (request.getRemarks() == null || request.getRemarks().isBlank()) {
            throw new IllegalArgumentException("Problem description is required");
        }

        BreakdownTicket ticket = new BreakdownTicket();
        ticket.setHospitalId(hospitalId);
        ticket.setEquipmentId(equipment.getId());
        ticket.setReportedBy(securityHelper.getCurrentUserEmail());
        ticket.setReportedAt(LocalDateTime.now());
        ticket.setPriority(priority);
        ticket.setRemarks(request.getRemarks());
        ticket.setStatus("OPEN");
        BreakdownTicket saved = ticketRepository.save(ticket);

        equipment.setStatus("DOWN");
        equipmentRepository.save(equipment);

        audit("BIOMEDICAL_TICKET_OPENED", "Ticket opened for asset " + equipment.getAssetCode()
                + " (" + priority + "): " + request.getRemarks(), hospitalId);
        return saved;
    }

    /** BR-2: records a calibration certificate; PASS clears the asset back to ACTIVE. */
    @Transactional
    public CalibrationRecord recordCalibration(CalibrationRequest request) {
        Long hospitalId = requireHospital();
        MedicalEquipment equipment = equipmentRepository.findByIdAndHospitalId(request.getEquipmentId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Equipment not found"));
        if (request.getCalibrationDate() == null || request.getDueDate() == null) {
            throw new IllegalArgumentException("Calibration date and due date are required");
        }
        if (request.getCalibrationDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Calibration date cannot be in the future");
        }
        if (!request.getDueDate().isAfter(request.getCalibrationDate())) {
            throw new IllegalArgumentException("Due date must be after the calibration date");
        }
        String result = request.getResult() == null ? "" : request.getResult().toUpperCase();
        if (!VALID_RESULTS.contains(result)) {
            throw new IllegalArgumentException("Result must be PASS or FAIL");
        }

        CalibrationRecord record = new CalibrationRecord();
        record.setHospitalId(hospitalId);
        record.setEquipmentId(equipment.getId());
        record.setCalibrationDate(request.getCalibrationDate());
        record.setDueDate(request.getDueDate());
        record.setAgency(request.getAgency());
        record.setCertificateReference(request.getCertificateReference());
        record.setResult(result);
        CalibrationRecord saved = calibrationRepository.save(record);

        if (!"RETIRED".equals(equipment.getStatus())) {
            equipment.setStatus("PASS".equals(result) ? "ACTIVE" : "DOWN");
            equipmentRepository.save(equipment);
        }

        audit("BIOMEDICAL_CALIBRATION_RECORDED", "Calibration " + result + " for asset " + equipment.getAssetCode()
                + " by " + request.getAgency() + ", due " + request.getDueDate(), hospitalId);
        return saved;
    }

    /**
     * BR-4: the ticket and its device stay open/DOWN until the department head explicitly
     * confirms the repair was tested and works.
     */
    @Transactional
    public BreakdownTicket closeTicket(Long ticketId, TicketCloseRequest request) {
        Long hospitalId = requireHospital();
        BreakdownTicket ticket = ticketRepository.findByIdAndHospitalId(ticketId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new IllegalStateException("This ticket has already been closed.");
        }
        if (!Boolean.TRUE.equals(request.getConfirmResolution())) {
            throw new IllegalArgumentException("Closing a ticket requires confirming the repair was tested (BR-4)");
        }

        ticket.setStatus("CLOSED");
        ticket.setResolvedAt(LocalDateTime.now());
        BreakdownTicket saved = ticketRepository.save(ticket);

        equipmentRepository.findByIdAndHospitalId(ticket.getEquipmentId(), hospitalId).ifPresent(equipment -> {
            if (!"RETIRED".equals(equipment.getStatus())) {
                equipment.setStatus("ACTIVE");
                equipmentRepository.save(equipment);
            }
        });

        audit("BIOMEDICAL_TICKET_CLOSED", "Ticket #" + ticket.getId() + " closed and confirmed repaired", hospitalId);
        return saved;
    }

    public List<BreakdownTicket> getTickets() {
        return ticketRepository.findByHospitalIdOrderByReportedAtDesc(requireHospital());
    }

    public List<CalibrationRecord> getCalibrations() {
        return calibrationRepository.findByHospitalIdOrderByCalibrationDateDesc(requireHospital());
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
