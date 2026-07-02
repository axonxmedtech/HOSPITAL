package com.hms.service.hospital;

import com.hms.dto.AnaesthesiaRecordRequest;
import com.hms.dto.ClinicalHandoverRequest;
import com.hms.dto.ImplantRecordRequest;
import com.hms.dto.InstrumentCountRequest;
import com.hms.dto.OperationRecordRequest;
import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.dto.PacRequest;
import com.hms.dto.PacuRecordRequest;
import com.hms.dto.PostopOrdersRequest;
import com.hms.dto.OtReadinessRequest;
import com.hms.dto.OtRegisterDto;
import com.hms.entity.*;
import java.util.Optional;

import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OtService {

    private static final Logger log = LoggerFactory.getLogger(OtService.class);

    @Autowired
    private OtBookingRepository bookingRepository;

    @Autowired
    private OtChecklistRepository checklistRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private BillingService billingService;

    @Autowired
    private MrdService mrdService;

    @Autowired
    private OperationRecordRepository operationRecordRepository;

    @Autowired
    private AnaesthesiaRecordRepository anaesthesiaRecordRepository;

    @Autowired
    private PacuRecordRepository pacuRecordRepository;

    @Autowired
    private ClinicalHandoverRepository clinicalHandoverRepository;

    @Autowired
    private PostopOrdersRepository postopOrdersRepository;

    @Autowired
    private OtInstrumentCountRepository instrumentCountRepository;

    @Autowired
    private PatientImplantRepository implantRepository;

    @Autowired
    private OtReadinessRepository readinessRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.DoctorOrderRepository doctorOrderRepository;

    @Autowired
    private com.hms.service.hospital.DoctorOrderService doctorOrderService;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.LabOrderRepository labOrderRepository;

    @Autowired
    private com.hms.repository.HospitalInventoryRepository inventoryRepository;

    @Autowired
    private com.hms.repository.StockTransactionRepository transactionRepository;

    @Autowired
    private com.hms.repository.IncidentReportRepository incidentReportRepository;

    @Autowired
    private com.hms.repository.NurseTaskRepository taskRepository;

    @Autowired
    private PreAnaesthesiaAssessmentRepository pacRepository;

    @Autowired
    private PatientConsentRepository patientConsentRepository;

    /** Minimum modified Aldrete score required to transfer a patient out of PACU to a ward. */
    private static final int ALDRETE_TRANSFER_MIN = 9;

    public List<OtBooking> getBookingsForAdmission(Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + ipdAdmissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        return bookingRepository.findByIpdAdmissionIdAndHospitalIdOrderByScheduledDateTimeDesc(ipdAdmissionId, hospitalId);
    }

    public List<OtBooking> getHospitalBookings() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId);
    }

    public OtChecklist getChecklist(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        return checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Checklist not found for booking: " + bookingId));
    }

    @Transactional
    public OtBooking scheduleBooking(Long ipdAdmissionId, OtBookingRequest request) {
        mrdService.validateAdmissionActive(ipdAdmissionId);
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + ipdAdmissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        // Form 15 Gating Check (BR-1/BR-3): when a PAC exists for this admission, scheduling
        // requires it APPROVED with a passing fitness status. No PAC = legacy flow unchanged.
        PreAnaesthesiaAssessment pac = pacRepository
                .findByAdmissionIdAndHospitalId(ipdAdmissionId, hospitalId).orElse(null);
        if (pac != null) {
            if (!"APPROVED".equalsIgnoreCase(pac.getStatus())) {
                throw new IllegalStateException("Cannot schedule surgery: the pre-anaesthesia assessment has not been approved yet.");
            }
            String fitness = pac.getFitnessStatus() == null ? "" : pac.getFitnessStatus().toUpperCase();
            if (!fitness.equals("FIT") && !fitness.equals("FIT_WITH_PRECAUTIONS")) {
                throw new IllegalStateException("Cannot schedule surgery: PAC fitness status is " + pac.getFitnessStatus()
                        + " — only FIT or FIT_WITH_PRECAUTIONS may proceed.");
            }
        }

        // Form 26 Gating Check: gate scheduleBooking only when a readiness record exists and is not READY
        java.time.LocalDate requestDate = request.getScheduledDateTime().toLocalDate();
        Optional<OtReadiness> readinessOpt = readinessRepository.findByOtRoomAndReadinessDateAndHospitalId(
                request.getOtRoomNumber(), requestDate, hospitalId
        );
        if (readinessOpt.isPresent() && !"READY".equalsIgnoreCase(readinessOpt.get().getStatus())) {
            throw new IllegalStateException("Room " + request.getOtRoomNumber() + " is not READY on " + requestDate);
        }

        // Room conflict check (warn or throw, let's throw if exact same time or conflict exists)

        List<OtBooking> existing = bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId);
        boolean conflict = existing.stream().anyMatch(b -> 
            "SCHEDULED".equals(b.getStatus()) &&
            request.getOtRoomNumber().equals(b.getOtRoomNumber()) &&
            Math.abs(java.time.Duration.between(request.getScheduledDateTime(), b.getScheduledDateTime()).toMinutes()) < 60
        );
        if (conflict) {
            throw new IllegalArgumentException("Room " + request.getOtRoomNumber() + " is already booked within 1 hour of this time.");
        }

        OtBooking booking = new OtBooking();
        booking.setIpdAdmissionId(ipdAdmissionId);
        booking.setHospitalId(hospitalId);
        booking.setProcedureName(request.getProcedureName());
        booking.setScheduledDateTime(request.getScheduledDateTime());
        booking.setSurgeonId(request.getSurgeonId());
        booking.setAnesthetistName(request.getAnesthetistName());
        booking.setOtRoomNumber(request.getOtRoomNumber());
        booking.setNotes(request.getNotes());
        booking.setStatus("SCHEDULED");

        OtBooking saved = bookingRepository.save(booking);

        // Pre-create checklist
        OtChecklist checklist = new OtChecklist();
        checklist.setOtBookingId(saved.getId());
        checklist.setHospitalId(hospitalId);
        checklistRepository.save(checklist);

        audit("OT_SURGERY_SCHEDULED", "Surgery scheduled: " + request.getProcedureName() + " in " + request.getOtRoomNumber(), hospitalId);
        broadcast(hospitalId);

        return saved;
    }

    @Transactional
    public OtBooking updateStatus(Long bookingId, String status) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        if (!List.of("SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid surgery status");
        }

        if ("IN_PROGRESS".equals(status)) {
            requireRoomReady(booking, hospitalId);
        }

        booking.setStatus(status);
        OtBooking saved = bookingRepository.save(booking);

        audit("OT_SURGERY_STATUS_UPDATED", "Surgery " + bookingId + " status changed to " + status, hospitalId);
        broadcast(hospitalId);

        return saved;
    }

    /**
     * Form 26 readiness gate: blocks a booking from starting (IN_PROGRESS) unless a readiness
     * record for that room/date is READY. Do-no-harm: absent record = legacy behavior, allowed.
     * Keyed off the booking's own scheduled date (matching scheduleBooking's lookup), not
     * "today" — a readiness record logged against the scheduled date must still gate the case
     * even if it's actually started a day later, and vice versa.
     */
    private void requireRoomReady(OtBooking booking, Long hospitalId) {
        if (booking.getScheduledDateTime() == null) {
            return; // no scheduled date to key the readiness lookup on — do-no-harm, skip the gate
        }
        java.time.LocalDate readinessDate = booking.getScheduledDateTime().toLocalDate();
        Optional<OtReadiness> readinessOpt = readinessRepository.findByOtRoomAndReadinessDateAndHospitalId(
                booking.getOtRoomNumber(), readinessDate, hospitalId
        );
        if (readinessOpt.isPresent() && !"READY".equalsIgnoreCase(readinessOpt.get().getStatus())) {
            throw new IllegalStateException("Room " + booking.getOtRoomNumber() + " is not READY on " + readinessDate);
        }
    }

    @Transactional
    public OtChecklist signChecklist(Long bookingId, OtChecklistRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        OtChecklist checklist = checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Checklist not found for booking: " + bookingId));

        String phase = request.getPhase();
        LocalDateTime now = LocalDateTime.now();

        if ("SIGN_IN".equalsIgnoreCase(phase)) {
            // Form 16 gate: when a SURGERY consent exists for this admission, WHO sign-in is
            // blocked until it is signed/submitted. No surgical consent = legacy flow unchanged.
            List<PatientConsent> consents = patientConsentRepository
                    .findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, booking.getIpdAdmissionId());
            boolean hasUnsignedSurgicalConsent = consents.stream()
                    .anyMatch(c -> "SURGERY".equalsIgnoreCase(c.getConsentType())
                            && "DRAFT".equalsIgnoreCase(c.getStatus()));
            if (hasUnsignedSurgicalConsent) {
                throw new IllegalStateException("Cannot sign in: the surgical consent has not been signed by the patient/guardian yet.");
            }
            checklist.setSignInCompleted(true);
            checklist.setSignInBy(email);
            checklist.setSignInAt(now);
            checklist.setSignInNotes(request.getNotes());
        } else if ("TIME_OUT".equalsIgnoreCase(phase)) {
            if (!checklist.isSignInCompleted()) {
                throw new IllegalStateException("Cannot sign Time Out before Sign In is completed.");
            }
            checklist.setTimeOutCompleted(true);
            checklist.setTimeOutBy(email);
            checklist.setTimeOutAt(now);
            checklist.setTimeOutNotes(request.getNotes());

            // Auto-advance status to IN_PROGRESS when time-out is signed off. This is the
            // real-world path a case actually starts through (not the standalone updateStatus
            // method), so the same room-readiness gate must apply here too — otherwise a case
            // can begin in an un-READY room by simply never calling updateStatus directly.
            requireRoomReady(booking, hospitalId);
            booking.setStatus("IN_PROGRESS");
            bookingRepository.save(booking);
        } else if ("SIGN_OUT".equalsIgnoreCase(phase)) {
            if (!checklist.isTimeOutCompleted()) {
                throw new IllegalStateException("Cannot sign Sign Out before Time Out is completed.");
            }
            // BR-2 (Form 23): when a count record exists, sign-out is blocked until the final
            // count is VERIFIED or a documented discrepancy resolution has been recorded.
            // (No count record = legacy flow, allowed unchanged.)
            OtInstrumentCount count = instrumentCountRepository
                    .findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
            if (count != null && !"VERIFIED".equalsIgnoreCase(count.getFinalCountStatus())
                    && !Boolean.TRUE.equals(count.getResolved())) {
                throw new IllegalStateException(
                        "Cannot sign out: the instrument/swab/needle count is not verified"
                                + (("MISMATCH".equalsIgnoreCase(count.getFinalCountStatus()))
                                        ? " — resolve the count discrepancy first."
                                        : "."));
            }
            checklist.setSignOutCompleted(true);
            checklist.setSignOutBy(email);
            checklist.setSignOutAt(now);
            checklist.setSignOutNotes(request.getNotes());

            // Auto-advance status to COMPLETED when sign-out is completed
            booking.setStatus("COMPLETED");
            bookingRepository.save(booking);

            // Auto-Billing
            try {
                billingService.postIpdCharge(booking.getIpdAdmissionId(), "Surgery Procedure - " + booking.getProcedureName(), new java.math.BigDecimal("5000.00"));
            } catch (Exception e) {
                log.warn("Failed to auto-bill surgery booking: {}", e.getMessage());
            }
        } else {
            throw new IllegalArgumentException("Invalid checklist phase: " + phase);
        }

        OtChecklist saved = checklistRepository.save(checklist);

        audit("OT_CHECKLIST_SIGNED", "Checklist phase " + phase + " signed for booking " + bookingId + " by " + email, hospitalId);
        broadcast(hospitalId);

        return saved;
    }

    // ===== Operation Record (Form 18 core) =====

    /** Loads the operation record for a booking (or null), tenant-guarded. */
    public OperationRecord getOperationRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** BR-1: create only after the WHO time-out is complete (patient inside OT). Upserts a DRAFT. */
    @Transactional
    public OperationRecord createOperationRecord(Long bookingId, OperationRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        OtChecklist checklist = checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Checklist not found for booking: " + bookingId));
        if (!checklist.isTimeOutCompleted()) {
            throw new IllegalStateException("Cannot start the operation record before the WHO time-out is completed.");
        }

        // Upsert: reuse an existing draft rather than violating the unique(ot_booking_id) constraint.
        OperationRecord record = operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(OperationRecord::new);
        if ("FINALIZED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Operation record is finalized and can no longer be edited.");
        }
        record.setHospitalId(hospitalId);
        record.setOtBookingId(bookingId);
        record.setAdmissionId(booking.getIpdAdmissionId());
        record.setSurgeonId(booking.getSurgeonId());
        applyRequest(record, request, booking);
        record.setStatus("DRAFT");
        OperationRecord saved = operationRecordRepository.save(record);

        audit("OT_OPERATION_RECORD_CREATED", "Operation record started for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Edit a DRAFT operation record. */
    @Transactional
    public OperationRecord updateOperationRecord(Long bookingId, OperationRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        OperationRecord record = operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Operation record not found for booking: " + bookingId));
        if ("FINALIZED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Operation record is finalized and can no longer be edited.");
        }
        applyRequest(record, request, booking);
        OperationRecord saved = operationRecordRepository.save(record);

        audit("OT_OPERATION_RECORD_UPDATED", "Operation record updated for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-2: finalize+sign — requires actual procedure, post-op plan and a completed WHO sign-out. */
    @Transactional
    public OperationRecord finalizeOperationRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        IpdAdmission admission = ipdAdmissionRepository.findById(booking.getIpdAdmissionId())
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + booking.getIpdAdmissionId()));
        Long patientId = admission.getPatientId();
        OperationRecord record = operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Operation record not found for booking: " + bookingId));

        // Re-entry guard: finalizing twice would re-run the specimen->LabOrder fan-out and
        // double-deduct implant stock below.
        if ("FINALIZED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Operation record is already finalized for booking: " + bookingId);
        }

        if (record.getActualProcedure() == null || record.getActualProcedure().isBlank()) {
            throw new IllegalStateException("Cannot finalize: the actual procedure performed is required.");
        }
        if (record.getPostOpPlan() == null || record.getPostOpPlan().isBlank()) {
            throw new IllegalStateException("Cannot finalize: a post-operative plan is required.");
        }
        OtChecklist checklist = checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Checklist not found for booking: " + bookingId));
        if (!checklist.isSignOutCompleted()) {
            throw new IllegalStateException("Cannot finalize: the WHO sign-out (instrument count) must be completed first.");
        }

        record.setStatus("FINALIZED");
        record.setSignedBy(email);
        record.setSignedAt(LocalDateTime.now());

        // 1. Specimens to LabOrders
        if (record.getSpecimens() != null && !record.getSpecimens().isBlank()) {
            String[] specLines = record.getSpecimens().split(";");
            for (String line : specLines) {
                String cleanLine = line.trim();
                if (cleanLine.isEmpty()) continue;

                com.hms.entity.LabOrder labOrder = new com.hms.entity.LabOrder();
                labOrder.setHospitalId(hospitalId);
                labOrder.setPatientId(patientId);
                labOrder.setIpdAdmissionId(record.getAdmissionId());
                labOrder.setTestName("Surgical Specimen Pathology: " + cleanLine);
                labOrder.setPriority("ROUTINE");
                labOrder.setStatus("ORDERED");
                labOrder.setOrderedByName(email);
                labOrderRepository.save(labOrder);
            }
        }

        // 2. Implants auto-deduct stock
        if (record.getImplants() != null && !record.getImplants().isBlank()) {
            String[] impLines = record.getImplants().split(";");
            for (String line : impLines) {
                String cleanLine = line.trim();
                if (cleanLine.isEmpty()) continue;

                String itemName = cleanLine;
                double qty = 1.0;
                if (cleanLine.contains("[") && cleanLine.contains("]")) {
                    try {
                        int start = cleanLine.indexOf("[");
                        int end = cleanLine.indexOf("]");
                        qty = Double.parseDouble(cleanLine.substring(start + 1, end).trim());
                        itemName = cleanLine.substring(0, start).trim();
                    } catch (Exception e) {
                        // ignore parsing error, default to 1.0
                    }
                }

                final double finalQty = qty;
                java.util.List<com.hms.entity.HospitalInventory> stockList = inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(itemName, hospitalId);
                if (!stockList.isEmpty()) {
                    com.hms.entity.HospitalInventory batch = stockList.stream()
                            .filter(b -> b.getStockQuantity() >= finalQty)
                            .findFirst()
                            .orElse(stockList.get(0));

                    int newQty = Math.max(0, batch.getStockQuantity() - (int) finalQty);
                    batch.setStockQuantity(newQty);
                    inventoryRepository.save(batch);

                    // Create StockTransaction
                    com.hms.entity.StockTransaction tx = new com.hms.entity.StockTransaction();
                    tx.setHospitalId(hospitalId);
                    tx.setInventoryItemId(batch.getId());
                    tx.setBatchId(batch.getId());
                    tx.setTransactionType("ISSUE");
                    tx.setQuantity(java.math.BigDecimal.valueOf(qty));
                    tx.setFromStore("OT Store");
                    tx.setToStore("Patient Implantation");
                    tx.setPerformedBy(email);
                    tx.setTransactionTime(LocalDateTime.now());
                    transactionRepository.save(tx);
                }
            }
        }

        // 3. Complications incident tracking
        if (record.getComplicationsSummary() != null && !record.getComplicationsSummary().isBlank() && !"NONE".equalsIgnoreCase(record.getComplicationsSummary().trim())) {
            com.hms.entity.IncidentReport incident = new com.hms.entity.IncidentReport();
            incident.setHospitalId(hospitalId);
            incident.setPatientId(patientId);
            incident.setAdmissionId(record.getAdmissionId());
            incident.setSource("OT_COMPLICATION");
            incident.setRelatedEntityId(record.getId());
            incident.setSeverity("HIGH");
            incident.setDescription("Intraoperative Complication: " + record.getComplicationsSummary());
            incident.setStatus("PENDING_REVIEW");
            incident.setReportedBy(email);
            incident.setReportedAt(LocalDateTime.now());
            incidentReportRepository.save(incident);
        }

        OperationRecord saved = operationRecordRepository.save(record);

        audit("OT_OPERATION_RECORD_FINALIZED", "Operation record finalized for booking " + bookingId + " by " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Copies editable fields, enforcing monotonic timings. */
    private void applyRequest(OperationRecord record, OperationRecordRequest request, OtBooking booking) {
        record.setProcedureName(
                (request.getProcedureName() != null && !request.getProcedureName().isBlank())
                        ? request.getProcedureName()
                        : booking.getProcedureName());
        record.setActualProcedure(request.getActualProcedure());
        record.setOperativeFindings(request.getOperativeFindings());
        record.setEstimatedBloodLoss(request.getEstimatedBloodLoss());
        record.setComplicationsSummary(request.getComplicationsSummary());
        record.setPostOpPlan(request.getPostOpPlan());
        record.setSpecimens(request.getSpecimens());
        record.setImplants(request.getImplants());
        if (request.getOperationStart() != null && request.getOperationEnd() != null
                && request.getOperationEnd().isBefore(request.getOperationStart())) {
            throw new IllegalArgumentException("Operation end time cannot be before the start time.");
        }
        record.setOperationStart(request.getOperationStart());
        record.setOperationEnd(request.getOperationEnd());
    }

    // ===== Anaesthesia Record (Form 19 core) =====

    /** Loads the anaesthesia record for a booking (or null), tenant-guarded. */
    public AnaesthesiaRecord getAnaesthesiaRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** BR-1: start only after the WHO sign-in (before induction of anaesthesia). Upserts an ACTIVE record. */
    @Transactional
    public AnaesthesiaRecord startAnaesthesiaRecord(Long bookingId, AnaesthesiaRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        OtChecklist checklist = checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Checklist not found for booking: " + bookingId));
        if (!checklist.isSignInCompleted()) {
            throw new IllegalStateException("Cannot start the anaesthesia record before the WHO sign-in is completed.");
        }

        AnaesthesiaRecord record = anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(AnaesthesiaRecord::new);
        if ("COMPLETED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Anaesthesia record is completed and can no longer be edited.");
        }
        record.setHospitalId(hospitalId);
        record.setOtBookingId(bookingId);
        record.setAdmissionId(booking.getIpdAdmissionId());
        applyAnaesthesiaRequest(record, request);
        record.setStatus("ACTIVE");
        AnaesthesiaRecord saved = anaesthesiaRecordRepository.save(record);

        audit("ANAESTHESIA_RECORD_STARTED", "Anaesthesia record started for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Edit an ACTIVE anaesthesia record. */
    @Transactional
    public AnaesthesiaRecord updateAnaesthesiaRecord(Long bookingId, AnaesthesiaRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        AnaesthesiaRecord record = anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Anaesthesia record not found for booking: " + bookingId));
        if ("COMPLETED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Anaesthesia record is completed and can no longer be edited.");
        }
        applyAnaesthesiaRequest(record, request);
        AnaesthesiaRecord saved = anaesthesiaRecordRepository.save(record);

        audit("ANAESTHESIA_RECORD_UPDATED", "Anaesthesia record updated for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-5 feeder: complete + sign the anaesthesia record (this is what gates PACU/recovery). */
    @Transactional
    public AnaesthesiaRecord completeAnaesthesiaRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        AnaesthesiaRecord record = anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Anaesthesia record not found for booking: " + bookingId));
        if (record.getAnaesthesiaType() == null || record.getAnaesthesiaType().isBlank()) {
            throw new IllegalStateException("Cannot complete: the anaesthesia type is required.");
        }

        record.setStatus("COMPLETED");
        if (record.getCompletionTime() == null) {
            record.setCompletionTime(LocalDateTime.now());
        }
        record.setSignedBy(email);
        record.setSignedAt(LocalDateTime.now());
        AnaesthesiaRecord saved = anaesthesiaRecordRepository.save(record);

        audit("ANAESTHESIA_RECORD_COMPLETED", "Anaesthesia record completed for booking " + bookingId + " by " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private void applyAnaesthesiaRequest(AnaesthesiaRecord record, AnaesthesiaRecordRequest request) {
        record.setAnaesthesiaType(request.getAnaesthesiaType());
        record.setAsaGrade(request.getAsaGrade());
        record.setAirwayType(request.getAirwayType());
        record.setVentilationMode(request.getVentilationMode());
        if (request.getInductionTime() != null && request.getCompletionTime() != null
                && request.getCompletionTime().isBefore(request.getInductionTime())) {
            throw new IllegalArgumentException("Completion time cannot be before the induction time.");
        }
        record.setInductionTime(request.getInductionTime());
        record.setCompletionTime(request.getCompletionTime());
        record.setNotes(request.getNotes());
    }

    // ===== PACU / Recovery Record (Form 20 core) =====

    /** Loads the PACU record for a booking (or null), tenant-guarded. */
    public PacuRecord getPacuRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** BR-1: recovery can only begin once the anaesthesia record is COMPLETED (Form 19 BR-5). */
    @Transactional
    public PacuRecord startPacuRecord(Long bookingId, PacuRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        AnaesthesiaRecord anaesthesia = anaesthesiaRecordRepository
                .findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
        if (anaesthesia == null || !"COMPLETED".equalsIgnoreCase(anaesthesia.getStatus())) {
            throw new IllegalStateException("Cannot start recovery before the anaesthesia record is completed.");
        }

        PacuRecord record = pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(PacuRecord::new);
        if ("TRANSFERRED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Patient has already been transferred out of recovery.");
        }
        record.setHospitalId(hospitalId);
        record.setOtBookingId(bookingId);
        record.setAdmissionId(booking.getIpdAdmissionId());
        if (record.getRecoveryStart() == null) {
            record.setRecoveryStart(request.getRecoveryStart() != null ? request.getRecoveryStart() : LocalDateTime.now());
        }
        applyPacuRequest(record, request);
        PacuRecord saved = pacuRecordRepository.save(record);

        audit("PACU_RECORD_STARTED", "Recovery record started for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Update an in-progress recovery record (recomputes Aldrete + READY status). */
    @Transactional
    public PacuRecord updatePacuRecord(Long bookingId, PacuRecordRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        PacuRecord record = pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("PACU record not found for booking: " + bookingId));
        if ("TRANSFERRED".equalsIgnoreCase(record.getStatus())) {
            throw new IllegalStateException("Patient has already been transferred; the record is read-only.");
        }
        applyPacuRequest(record, request);
        PacuRecord saved = pacuRecordRepository.save(record);

        audit("PACU_RECORD_UPDATED", "Recovery record updated for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-4 + BR-6: transfer requires Aldrete >= 9, a destination and a handover note. */
    @Transactional
    public PacuRecord transferPacuRecord(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        PacuRecord record = pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("PACU record not found for booking: " + bookingId));

        Integer score = record.getAldreteScore();
        if (score == null || score < ALDRETE_TRANSFER_MIN) {
            throw new IllegalStateException(
                    "Cannot transfer: modified Aldrete score must be at least " + ALDRETE_TRANSFER_MIN
                            + " (current: " + (score == null ? 0 : score) + ").");
        }
        if (record.getTransferDestination() == null || record.getTransferDestination().isBlank()) {
            throw new IllegalStateException("Cannot transfer: a transfer destination is required.");
        }
        if (record.getHandoverNotes() == null || record.getHandoverNotes().isBlank()) {
            throw new IllegalStateException("Cannot transfer: a structured handover note is required.");
        }

        record.setStatus("TRANSFERRED");
        record.setRecoveryEnd(LocalDateTime.now());
        record.setSignedBy(email);
        record.setSignedAt(LocalDateTime.now());
        PacuRecord saved = pacuRecordRepository.save(record);

        audit("PACU_RECORD_TRANSFERRED",
                "Recovery transfer to " + record.getTransferDestination() + " for booking " + bookingId + " by " + email,
                hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private void applyPacuRequest(PacuRecord record, PacuRecordRequest request) {
        record.setRecoveryBed(request.getRecoveryBed());
        if (request.getRecoveryStart() != null) record.setRecoveryStart(request.getRecoveryStart());
        if (request.getRecoveryEnd() != null) record.setRecoveryEnd(request.getRecoveryEnd());
        record.setConsciousness(request.getConsciousness());
        record.setOrientation(request.getOrientation());
        record.setAirwayStatus(request.getAirwayStatus());
        record.setBreathingStatus(request.getBreathingStatus());
        record.setCirculationStatus(request.getCirculationStatus());
        record.setNauseaSeverity(request.getNauseaSeverity());
        record.setVomitingPresent(request.getVomitingPresent());
        record.setPainScore(request.getPainScore());

        record.setAldreteActivity(validAldrete(request.getAldreteActivity(), "Activity"));
        record.setAldreteRespiration(validAldrete(request.getAldreteRespiration(), "Respiration"));
        record.setAldreteCirculation(validAldrete(request.getAldreteCirculation(), "Circulation"));
        record.setAldreteConsciousness(validAldrete(request.getAldreteConsciousness(), "Consciousness"));
        record.setAldreteOxygen(validAldrete(request.getAldreteOxygen(), "Oxygen saturation"));

        record.setTransferDestination(request.getTransferDestination());
        record.setHandoverNotes(request.getHandoverNotes());

        // BR-3: server-computes the modified Aldrete score as the sum of its 5 components.
        int score = nz(record.getAldreteActivity()) + nz(record.getAldreteRespiration())
                + nz(record.getAldreteCirculation()) + nz(record.getAldreteConsciousness())
                + nz(record.getAldreteOxygen());
        record.setAldreteScore(score);

        // Ready-for-transfer flag (BR-4); never downgrade a TRANSFERRED record here.
        if (!"TRANSFERRED".equalsIgnoreCase(record.getStatus())) {
            record.setStatus(score >= ALDRETE_TRANSFER_MIN ? "READY" : "ACTIVE");
        }
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private Integer validAldrete(Integer v, String label) {
        if (v == null) return null;
        if (v < 0 || v > 2) {
            throw new IllegalArgumentException("Aldrete " + label + " score must be 0, 1 or 2.");
        }
        return v;
    }

    // ===== Clinical Handover (Form 22 core) =====

    /** Loads the clinical handover for a booking (or null), tenant-guarded. */
    public ClinicalHandover getClinicalHandover(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** BR-1: initiate only when the PACU record is recovery-ready (Aldrete >= 9). BR-5: devices required. */
    @Transactional
    public ClinicalHandover initiateHandover(Long bookingId, ClinicalHandoverRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        PacuRecord pacu = pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
        if (pacu == null || pacu.getAldreteScore() == null || pacu.getAldreteScore() < ALDRETE_TRANSFER_MIN) {
            throw new IllegalStateException("Cannot initiate handover before the patient is recovery-ready (Aldrete >= "
                    + ALDRETE_TRANSFER_MIN + ").");
        }
        if (request.getTransportStaff() == null || request.getTransportStaff().isBlank()) {
            throw new IllegalStateException("Cannot initiate handover: the accompanying transport staff is required.");
        }
        if (request.getDevices() == null || request.getDevices().isBlank()) {
            throw new IllegalStateException("Cannot initiate handover: tubes/drains/lines must be documented (BR-5).");
        }

        ClinicalHandover handover = clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(ClinicalHandover::new);
        if ("ACCEPTED".equalsIgnoreCase(handover.getStatus())) {
            throw new IllegalStateException("Handover has already been accepted and is locked.");
        }
        handover.setHospitalId(hospitalId);
        handover.setOtBookingId(bookingId);
        handover.setAdmissionId(booking.getIpdAdmissionId());
        applyHandoverRequest(handover, request);
        handover.setHandoverBy(email);
        handover.setTransferTime(LocalDateTime.now());
        handover.setStatus("PENDING");
        ClinicalHandover saved = clinicalHandoverRepository.save(handover);

        audit("CLINICAL_HANDOVER_INITIATED", "Clinical handover initiated for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Update a PENDING handover (locked once accepted). */
    @Transactional
    public ClinicalHandover updateHandover(Long bookingId, ClinicalHandoverRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        ClinicalHandover handover = clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Clinical handover not found for booking: " + bookingId));
        if ("ACCEPTED".equalsIgnoreCase(handover.getStatus())) {
            throw new IllegalStateException("Handover has been accepted and is locked (BR-6).");
        }
        applyHandoverRequest(handover, request);
        ClinicalHandover saved = clinicalHandoverRepository.save(handover);

        audit("CLINICAL_HANDOVER_UPDATED", "Clinical handover updated for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-2 + BR-6: the receiving nurse explicitly accepts; the record then locks. */
    @Transactional
    public ClinicalHandover acceptHandover(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        ClinicalHandover handover = clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Clinical handover not found for booking: " + bookingId));
        if (!"PENDING".equalsIgnoreCase(handover.getStatus())) {
            throw new IllegalStateException("Only a pending handover can be accepted.");
        }

        handover.setStatus("ACCEPTED");
        handover.setAcceptedBy(email);
        handover.setAcceptedTime(LocalDateTime.now());
        ClinicalHandover saved = clinicalHandoverRepository.save(handover);

        audit("CLINICAL_HANDOVER_ACCEPTED", "Clinical handover accepted for booking " + bookingId + " by " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private void applyHandoverRequest(ClinicalHandover handover, ClinicalHandoverRequest request) {
        handover.setFromDepartment(request.getFromDepartment());
        handover.setToDepartment(request.getToDepartment());
        handover.setTransportMode(request.getTransportMode());
        handover.setTransportStaff(request.getTransportStaff());
        handover.setDevices(request.getDevices());
        handover.setMonitoringPlan(request.getMonitoringPlan());
        handover.setPendingTasks(request.getPendingTasks());
        handover.setRemarks(request.getRemarks());
    }

    // ===== Post-operative Orders (Form 21 core) =====

    /** Loads the post-op orders for a booking (or null), tenant-guarded. */
    public PostopOrders getPostopOrders(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** Upsert a DRAFT post-op order bundle (blocked once SIGNED, BR-6). */
    @Transactional
    public PostopOrders savePostopOrders(Long bookingId, PostopOrdersRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        PostopOrders orders = postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(PostopOrders::new);
        if ("SIGNED".equalsIgnoreCase(orders.getStatus())) {
            throw new IllegalStateException("Post-operative orders are signed and read-only; create an amendment instead (BR-6).");
        }
        orders.setHospitalId(hospitalId);
        orders.setOtBookingId(bookingId);
        orders.setAdmissionId(booking.getIpdAdmissionId());
        orders.setSurgeonId(booking.getSurgeonId());
        applyPostopRequest(orders, request);
        orders.setStatus("DRAFT");
        PostopOrders saved = postopOrdersRepository.save(orders);

        audit("POSTOP_ORDERS_SAVED", "Post-op orders drafted for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-1/BR-6: surgeon signs the bundle; requires a post-op diagnosis; the record then locks. */
    @Transactional
    public PostopOrders signPostopOrders(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        IpdAdmission admission = ipdAdmissionRepository.findById(booking.getIpdAdmissionId())
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + booking.getIpdAdmissionId()));
        Long patientId = admission.getPatientId();
        PostopOrders orders = postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Post-operative orders not found for booking: " + bookingId));
        if ("SIGNED".equalsIgnoreCase(orders.getStatus())) {
            throw new IllegalStateException("Post-operative orders are already signed.");
        }
        if (orders.getPostopDiagnosis() == null || orders.getPostopDiagnosis().isBlank()) {
            throw new IllegalStateException("Cannot sign: a post-operative diagnosis is required.");
        }

        orders.setStatus("SIGNED");
        orders.setSignedBy(email);
        orders.setSignedAt(LocalDateTime.now());

        // Trigger 1 & 3: Post-op meds -> Pharmacy queue + NurseTask MAR schedules + Monitoring/vitals task generation
        if (orders.getMedications() != null && !orders.getMedications().isBlank()) {
            com.hms.entity.MedicalRecord medRecord = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(orders.getAdmissionId())
                    .stream().findFirst().orElseGet(() -> {
                        com.hms.entity.MedicalRecord mr = new com.hms.entity.MedicalRecord();
                        mr.setHospitalId(hospitalId);
                        mr.setPatientId(patientId);
                        mr.setIpdAdmissionId(orders.getAdmissionId());
                        mr.setDoctorId(booking.getSurgeonId());
                        mr.setSymptoms("Post-operative recovery");
                        return medicalRecordRepository.save(mr);
                    });

            String[] medLines = orders.getMedications().split(";");
            for (String line : medLines) {
                String cleanLine = line.trim();
                if (cleanLine.isEmpty()) continue;

                // Create DoctorOrder (Type: MEDICATION)
                DoctorOrder docOrder = new DoctorOrder();
                docOrder.setHospitalId(hospitalId);
                docOrder.setIpdAdmissionId(orders.getAdmissionId());
                docOrder.setOrderType("MEDICATION");
                docOrder.setDescription(cleanLine);
                docOrder.setStartDate(java.time.LocalDate.now());
                docOrder.setEndDate(java.time.LocalDate.now().plusDays(3));

                String freq = "OD";
                if (cleanLine.toUpperCase().contains("BD")) freq = "BD";
                else if (cleanLine.toUpperCase().contains("TDS")) freq = "TDS";
                else if (cleanLine.toUpperCase().contains("QID")) freq = "QID";
                else if (cleanLine.toUpperCase().contains("SOS")) freq = "SOS";
                docOrder.setFrequency(freq);
                docOrder.setStatus("ACTIVE");
                docOrder.setCreatedByName(email);
                doctorOrderRepository.save(docOrder);

                // Create NurseTask schedules for the doctor order
                if (!"SOS".equalsIgnoreCase(freq)) {
                    doctorOrderService.createTaskForOrder(docOrder, LocalDateTime.now());
                }

                // Create Prescription for Pharmacy queue
                com.hms.entity.Prescription rx = new com.hms.entity.Prescription();
                rx.setHospitalId(hospitalId);
                rx.setMedicalRecordId(medRecord.getId());
                rx.setMedicineName(cleanLine);
                rx.setDosage("As prescribed");
                rx.setFrequency(freq);
                rx.setDuration("3 Days");
                rx.setDurationDays(3);
                rx.setStartDate(java.time.LocalDate.now());
                rx.setStatus("ACTIVE");
                rx.setRoute("IV");
                rx.setType("INJECTION");
                prescriptionRepository.save(rx);
            }
        }

        // 2. Monitoring (Vitals tasks)
        if (orders.getMonitoringPlan() != null && !orders.getMonitoringPlan().isBlank()) {
            String[] planLines = orders.getMonitoringPlan().split(";");
            for (String planLine : planLines) {
                String cleanPlan = planLine.trim();
                if (cleanPlan.isEmpty()) continue;

                // Create a vitals monitoring NurseTask directly
                NurseTask vitalTask = new NurseTask();
                vitalTask.setHospitalId(hospitalId);
                vitalTask.setIpdAdmissionId(orders.getAdmissionId());
                vitalTask.setScheduledAt(LocalDateTime.now());
                vitalTask.setStatus("PENDING");
                vitalTask.setSource("RISK_PROTOCOL");
                vitalTask.setTaskType("OBSERVATION");
                vitalTask.setNotes("Vitals Check: " + cleanPlan);
                taskRepository.save(vitalTask);
            }
        }

        // 3. Investigations (LabOrders)
        if (orders.getInvestigations() != null && !orders.getInvestigations().isBlank()) {
            String[] invLines = orders.getInvestigations().split(";");
            for (String invLine : invLines) {
                String cleanInv = invLine.trim();
                if (cleanInv.isEmpty()) continue;

                // Create DoctorOrder (Type: INVESTIGATION)
                DoctorOrder docOrder = new DoctorOrder();
                docOrder.setHospitalId(hospitalId);
                docOrder.setIpdAdmissionId(orders.getAdmissionId());
                docOrder.setOrderType("INVESTIGATION");
                docOrder.setDescription(cleanInv);
                docOrder.setStartDate(java.time.LocalDate.now());
                docOrder.setStatus("ACTIVE");
                docOrder.setCreatedByName(email);
                doctorOrderRepository.save(docOrder);

                // Auto-generate LabOrder
                com.hms.entity.LabOrder labOrder = new com.hms.entity.LabOrder();
                labOrder.setHospitalId(hospitalId);
                labOrder.setPatientId(patientId);
                labOrder.setIpdAdmissionId(orders.getAdmissionId());
                labOrder.setTestName(cleanInv);
                labOrder.setPriority("ROUTINE");
                labOrder.setStatus("ORDERED");
                labOrder.setOrderedByName(email);
                labOrderRepository.save(labOrder);
            }
        }

        PostopOrders saved = postopOrdersRepository.save(orders);

        audit("POSTOP_ORDERS_SIGNED", "Post-op orders signed for booking " + bookingId + " by " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private void applyPostopRequest(PostopOrders orders, PostopOrdersRequest request) {
        orders.setPostopDiagnosis(request.getPostopDiagnosis());
        orders.setCondition(request.getCondition());
        orders.setDietOrder(request.getDietOrder());
        orders.setActivityOrder(request.getActivityOrder());
        orders.setMedications(request.getMedications());
        orders.setMonitoringPlan(request.getMonitoringPlan());
        orders.setInvestigations(request.getInvestigations());
        orders.setEscalationInstructions(request.getEscalationInstructions());
    }

    // ===== Instrument / Swab / Needle Count (Form 23 core) =====

    /** Loads the instrument count for a booking (or null), tenant-guarded. */
    public OtInstrumentCount getInstrumentCount(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId).orElse(null);
    }

    /** Upsert the count session (BR-3: totals update as items are opened). */
    @Transactional
    public OtInstrumentCount saveInstrumentCount(Long bookingId, InstrumentCountRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        String initialStatus = normalizeCountStatus(request.getInitialCountStatus(), false);
        String finalStatus = normalizeCountStatus(request.getFinalCountStatus(), true);

        OtInstrumentCount count = instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseGet(OtInstrumentCount::new);
        count.setHospitalId(hospitalId);
        count.setOtBookingId(bookingId);
        count.setAdmissionId(booking.getIpdAdmissionId());
        count.setScrubNurse(request.getScrubNurse());
        count.setCirculatingNurse(request.getCirculatingNurse());
        count.setCountSummary(request.getCountSummary());
        count.setInitialCountStatus(initialStatus);
        count.setFinalCountStatus(finalStatus);
        if ("MISMATCH".equals(finalStatus)) {
            count.setDiscrepancyFound(true);
            count.setResolved(false); // a (re-)declared mismatch always requires fresh resolution documentation
        } else if ("VERIFIED".equals(finalStatus)) {
            count.setCompletedAt(LocalDateTime.now());
        }
        OtInstrumentCount saved = instrumentCountRepository.save(count);

        audit("OT_INSTRUMENT_COUNT_SAVED", "Instrument count " + finalStatus + " for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-4: document a discrepancy resolution (search + remarks required) so sign-out can proceed. */
    @Transactional
    public OtInstrumentCount resolveInstrumentCount(Long bookingId, InstrumentCountRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT Booking not found: " + bookingId));
        if (!booking.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        IpdAdmission admission = ipdAdmissionRepository.findById(booking.getIpdAdmissionId())
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + booking.getIpdAdmissionId()));
        Long patientId = admission.getPatientId();
        OtInstrumentCount count = instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Instrument count not found for booking: " + bookingId));
        if (!"MISMATCH".equalsIgnoreCase(count.getFinalCountStatus())) {
            throw new IllegalStateException("Only a mismatched count can be resolved.");
        }
        if (!Boolean.TRUE.equals(request.getSearchConducted())) {
            throw new IllegalStateException("Cannot resolve: a search for the missing item must be conducted and documented.");
        }
        if (request.getResolutionRemarks() == null || request.getResolutionRemarks().isBlank()) {
            throw new IllegalStateException("Cannot resolve: resolution remarks are required.");
        }

        count.setSearchConducted(true);
        count.setXrayPerformed(Boolean.TRUE.equals(request.getXrayPerformed()));
        count.setResolutionRemarks(request.getResolutionRemarks());
        count.setResolved(true);
        count.setCompletedAt(LocalDateTime.now());

        // Auto-generate IncidentReport for count discrepancy
        if (Boolean.TRUE.equals(count.getDiscrepancyFound())) {
            com.hms.entity.IncidentReport incident = new com.hms.entity.IncidentReport();
            incident.setHospitalId(hospitalId);
            incident.setPatientId(patientId);
            incident.setAdmissionId(count.getAdmissionId());
            incident.setSource("OT_INSTRUMENT_COUNT");
            incident.setRelatedEntityId(count.getId());
            incident.setSeverity("MEDIUM");
            incident.setDescription("Instrument count mismatch: " + count.getCountSummary() + ". Resolution remarks: " + count.getResolutionRemarks());
            incident.setStatus("PENDING_REVIEW");
            incident.setReportedBy(email);
            incident.setReportedAt(LocalDateTime.now());
            incidentReportRepository.save(incident);
        }

        OtInstrumentCount saved = instrumentCountRepository.save(count);

        audit("OT_INSTRUMENT_COUNT_RESOLVED",
                "Count discrepancy resolved for booking " + bookingId + " by " + email
                        + (Boolean.TRUE.equals(request.getXrayPerformed()) ? " (X-ray performed)" : ""),
                hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private String normalizeCountStatus(String status, boolean allowMismatch) {
        if (status == null || status.isBlank()) return "PENDING";
        String s = status.toUpperCase();
        if (s.equals("PENDING") || s.equals("VERIFIED") || (allowMismatch && s.equals("MISMATCH"))) {
            return s;
        }
        throw new IllegalArgumentException("Invalid count status: " + status);
    }

    // ===== Implant Record (Form 24) =====

    public List<PatientImplant> getImplants(Long bookingId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT booking not found"));
        if (!booking.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        return implantRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId);
    }

    @Transactional
    public PatientImplant addImplant(Long bookingId, ImplantRecordRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("OT booking not found"));
        if (!booking.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied: Tenant mismatch");

        // Gate: operation record must exist and must not be FINALIZED
        OperationRecord opRec = operationRecordRepository
                .findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new IllegalStateException(
                        "An operation record must be created before adding implants"));
        if ("FINALIZED".equalsIgnoreCase(opRec.getStatus()))
            throw new IllegalStateException("Cannot add implants to a finalized operation record");

        // BR-7: serial number must be unique within the tenant
        if (req.getSerialNumber() != null && !req.getSerialNumber().isBlank()) {
            List<PatientImplant> existing =
                    implantRepository.findBySerialNumberAndHospitalId(req.getSerialNumber(), hospitalId);
            if (!existing.isEmpty())
                throw new IllegalArgumentException(
                        "Serial number '" + req.getSerialNumber() + "' already registered in this hospital");
        }

        mrdService.validateAdmissionActive(booking.getIpdAdmissionId());

        // Resolve patientId from the IPD admission
        Long patientId = null;
        try {
            IpdAdmission adm = ipdAdmissionRepository.findById(booking.getIpdAdmissionId()).orElse(null);
            if (adm != null) patientId = adm.getPatientId();
        } catch (Exception ignored) {}

        String actor = securityHelper.getCurrentUserEmail();
        PatientImplant implant = new PatientImplant();
        implant.setHospitalId(hospitalId);
        implant.setIpdAdmissionId(booking.getIpdAdmissionId());
        implant.setOtBookingId(bookingId);
        implant.setPatientId(patientId);
        mapImplantFields(implant, req);
        implant.setStatus("DRAFT");
        implant.setRecordedByName(actor);
        PatientImplant saved = implantRepository.save(implant);

        audit("IMPLANT_ADDED", "Implant '" + saved.getImplantName() + "' added for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    @Transactional
    public PatientImplant updateImplant(Long bookingId, Long implantId, ImplantRecordRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        PatientImplant implant = implantRepository.findById(implantId)
                .orElseThrow(() -> new RuntimeException("Implant record not found"));
        if (!implant.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        if (!implant.getOtBookingId().equals(bookingId))
            throw new IllegalArgumentException("Implant does not belong to this booking");
        if ("SIGNED".equalsIgnoreCase(implant.getStatus()))
            throw new IllegalStateException("Cannot modify a signed implant record");

        // Gate: operation record must still be open
        OperationRecord opRec = operationRecordRepository
                .findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new IllegalStateException("Operation record not found"));
        if ("FINALIZED".equalsIgnoreCase(opRec.getStatus()))
            throw new IllegalStateException("Cannot modify implants after operation record is finalized");

        // BR-7: if serial changed, check uniqueness
        if (req.getSerialNumber() != null && !req.getSerialNumber().equals(implant.getSerialNumber())) {
            List<PatientImplant> existing =
                    implantRepository.findBySerialNumberAndHospitalId(req.getSerialNumber(), hospitalId);
            if (!existing.isEmpty())
                throw new IllegalArgumentException(
                        "Serial number '" + req.getSerialNumber() + "' already registered in this hospital");
        }

        mapImplantFields(implant, req);
        PatientImplant saved = implantRepository.save(implant);
        broadcast(hospitalId);
        return saved;
    }

    @Transactional
    public PatientImplant signImplant(Long bookingId, Long implantId, String surgeonSig) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        PatientImplant implant = implantRepository.findById(implantId)
                .orElseThrow(() -> new RuntimeException("Implant record not found"));
        if (!implant.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        if (!implant.getOtBookingId().equals(bookingId))
            throw new IllegalArgumentException("Implant does not belong to this booking");
        if ("SIGNED".equalsIgnoreCase(implant.getStatus()))
            throw new IllegalStateException("Implant record already signed");

        implant.setSurgeonSig(surgeonSig);
        implant.setStatus("SIGNED");
        implant.setSignedBy(securityHelper.getCurrentUserId());
        implant.setSignedAt(LocalDateTime.now());
        PatientImplant saved = implantRepository.save(implant);

        audit("IMPLANT_SIGNED", "Implant '" + saved.getImplantName() + "' signed for booking " + bookingId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private void mapImplantFields(PatientImplant implant, ImplantRecordRequest req) {
        if (req.getInventoryItemId() != null) implant.setInventoryItemId(req.getInventoryItemId());
        if (req.getImplantName() != null) implant.setImplantName(req.getImplantName());
        if (req.getManufacturer() != null) implant.setManufacturer(req.getManufacturer());
        if (req.getModelNumber() != null) implant.setModelNumber(req.getModelNumber());
        if (req.getCatalogNumber() != null) implant.setCatalogNumber(req.getCatalogNumber());
        if (req.getBatchNumber() != null) implant.setBatchNumber(req.getBatchNumber());
        if (req.getLotNumber() != null) implant.setLotNumber(req.getLotNumber());
        if (req.getSerialNumber() != null) implant.setSerialNumber(req.getSerialNumber());
        if (req.getUdi() != null) implant.setUdi(req.getUdi());
        if (req.getExpiryDate() != null) implant.setExpiryDate(req.getExpiryDate());
        if (req.getQuantityOpened() != null) implant.setQuantityOpened(req.getQuantityOpened());
        if (req.getQuantityImplanted() != null) implant.setQuantityImplanted(req.getQuantityImplanted());
        if (req.getQuantityReturned() != null) implant.setQuantityReturned(req.getQuantityReturned());
        if (req.getQuantityWasted() != null) implant.setQuantityWasted(req.getQuantityWasted());
        if (req.getImplantLocation() != null) implant.setImplantLocation(req.getImplantLocation());
        if (req.getWarrantyCardNumber() != null) implant.setWarrantyCardNumber(req.getWarrantyCardNumber());
        if (req.getPatientCardIssued() != null) implant.setPatientCardIssued(req.getPatientCardIssued());
        if (req.getNurseSig() != null) implant.setNurseSig(req.getNurseSig());
    }

    // ===== Pre-Anaesthesia Assessment / PAC (Form 15 core) =====

    /** Loads the PAC for an admission (or null), tenant-guarded. */
    public PreAnaesthesiaAssessment getPac(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + admissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        return pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId).orElse(null);
    }

    /** Upsert a DRAFT PAC (blocked once APPROVED — amend requires a fresh clinical decision). */
    @Transactional
    public PreAnaesthesiaAssessment savePac(Long admissionId, PacRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + admissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        mrdService.validateAdmissionActive(admissionId);

        PreAnaesthesiaAssessment pac = pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)
                .orElseGet(PreAnaesthesiaAssessment::new);
        if ("APPROVED".equalsIgnoreCase(pac.getStatus())) {
            throw new IllegalStateException("PAC is approved and read-only.");
        }
        pac.setHospitalId(hospitalId);
        pac.setAdmissionId(admissionId);
        pac.setPatientId(admission.getPatientId());
        pac.setAsaClass(request.getAsaClass());
        pac.setAirwayAssessment(request.getAirwayAssessment());
        pac.setSystemicAssessment(request.getSystemicAssessment());
        pac.setFitnessStatus(normalizeFitness(request.getFitnessStatus()));
        pac.setPlannedAnaesthesia(request.getPlannedAnaesthesia());
        pac.setRemarks(request.getRemarks());
        pac.setStatus("DRAFT");
        PreAnaesthesiaAssessment saved = pacRepository.save(pac);

        audit("PAC_SAVED", "Pre-anaesthesia assessment drafted for admission " + admissionId, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** BR-2: approval requires the ASA class and a fitness decision; stamps the anaesthetist's signature. */
    @Transactional
    public PreAnaesthesiaAssessment approvePac(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + admissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }
        PreAnaesthesiaAssessment pac = pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)
                .orElseThrow(() -> new RuntimeException("PAC not found for admission: " + admissionId));
        if ("APPROVED".equalsIgnoreCase(pac.getStatus())) {
            throw new IllegalStateException("PAC is already approved.");
        }
        if (pac.getAsaClass() == null || pac.getAsaClass().isBlank()) {
            throw new IllegalStateException("Cannot approve: the ASA classification is mandatory (BR-2).");
        }
        if (pac.getFitnessStatus() == null || pac.getFitnessStatus().isBlank()) {
            throw new IllegalStateException("Cannot approve: a fitness decision is required.");
        }

        pac.setStatus("APPROVED");
        pac.setSignedBy(email);
        pac.setSignedAt(LocalDateTime.now());
        PreAnaesthesiaAssessment saved = pacRepository.save(pac);

        audit("PAC_APPROVED", "Pre-anaesthesia assessment approved for admission " + admissionId
                + " (fitness: " + pac.getFitnessStatus() + ") by " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    private String normalizeFitness(String fitness) {
        if (fitness == null || fitness.isBlank()) return null;
        String f = fitness.toUpperCase();
        if (f.equals("FIT") || f.equals("FIT_WITH_PRECAUTIONS")
                || f.equals("FURTHER_EVALUATION") || f.equals("DEFERRED")) {
            return f;
        }
        throw new IllegalArgumentException("Invalid fitness status: " + fitness);
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

    // ===== OT Register (Form 25) & OT Readiness (Form 26) =====

    public List<OtRegisterDto> getRegisterEntries(java.time.LocalDate date, String room) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<OtBooking> bookings = bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId);
        
        java.util.List<OtRegisterDto> result = new java.util.ArrayList<>();
        for (OtBooking b : bookings) {
            // Apply filtering by date and room
            if (date != null && !b.getScheduledDateTime().toLocalDate().equals(date)) {
                continue;
            }
            if (room != null && !room.equalsIgnoreCase(b.getOtRoomNumber())) {
                continue;
            }

            OtRegisterDto dto = new OtRegisterDto();
            dto.setBookingId(b.getId());
            dto.setProcedureName(b.getProcedureName());
            dto.setScheduledDateTime(b.getScheduledDateTime());
            dto.setAnesthetistName(b.getAnesthetistName());
            dto.setOtRoomNumber(b.getOtRoomNumber());
            dto.setStatus(b.getStatus());

            // Patient details
            try {
                IpdAdmission adm = ipdAdmissionRepository.findById(b.getIpdAdmissionId()).orElse(null);
                if (adm != null) {
                    dto.setIpdNumber(adm.getIpdNumber());
                    Patient p = patientRepository.findById(adm.getPatientId()).orElse(null);
                    if (p != null) {
                        dto.setPatientName(p.getName());
                        dto.setPatientCustomId(p.getCustomId());
                        dto.setPatientAge(p.getAge());
                        dto.setPatientGender(p.getGender());
                    }
                }
            } catch (Exception ignored) {}

            // Surgeon details
            try {
                Doctor doc = doctorRepository.findById(b.getSurgeonId()).orElse(null);
                if (doc != null) {
                    dto.setSurgeonName(doc.getName());
                }
            } catch (Exception ignored) {}

            // Operation Record status
            try {
                Optional<OperationRecord> op = operationRecordRepository.findByOtBookingIdAndHospitalId(b.getId(), hospitalId);
                dto.setOperationStatus(op.map(OperationRecord::getStatus).orElse("NONE"));
            } catch (Exception ignored) {
                dto.setOperationStatus("NONE");
            }

            // Anaesthesia Record status
            try {
                Optional<AnaesthesiaRecord> ana = anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(b.getId(), hospitalId);
                dto.setAnaesthesiaStatus(ana.map(AnaesthesiaRecord::getStatus).orElse("NONE"));
            } catch (Exception ignored) {
                dto.setAnaesthesiaStatus("NONE");
            }

            // PACU Record status
            try {
                Optional<PacuRecord> pacu = pacuRecordRepository.findByOtBookingIdAndHospitalId(b.getId(), hospitalId);
                if (pacu.isPresent()) {
                    dto.setPacuStatus(pacu.get().getStatus());
                } else {
                    dto.setPacuStatus("NONE");
                }
            } catch (Exception ignored) {
                dto.setPacuStatus("NONE");
            }

            // Checklist status
            try {
                Optional<OtChecklist> chk = checklistRepository.findByOtBookingIdAndHospitalId(b.getId(), hospitalId);
                if (chk.isPresent()) {
                    if (chk.get().isSignOutCompleted()) {
                        dto.setChecklistStatus("SIGN_OUT");
                    } else if (chk.get().isTimeOutCompleted()) {
                        dto.setChecklistStatus("TIME_OUT");
                    } else if (chk.get().isSignInCompleted()) {
                        dto.setChecklistStatus("SIGN_IN");
                    } else {
                        dto.setChecklistStatus("NONE");
                    }
                } else {
                    dto.setChecklistStatus("NONE");
                }
            } catch (Exception ignored) {
                dto.setChecklistStatus("NONE");
            }

            result.add(dto);
        }
        return result;
    }

    public java.util.Optional<OtReadiness> getReadiness(java.time.LocalDate date, String room) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return readinessRepository.findByOtRoomAndReadinessDateAndHospitalId(room, date, hospitalId);
    }

    @Transactional
    public OtReadiness saveReadiness(OtReadinessRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        OtReadiness readiness = readinessRepository.findByOtRoomAndReadinessDateAndHospitalId(
                req.getOtRoom(), req.getReadinessDate(), hospitalId
        ).orElseGet(() -> {
            OtReadiness r = new OtReadiness();
            r.setHospitalId(hospitalId);
            r.setOtRoom(req.getOtRoom());
            r.setReadinessDate(req.getReadinessDate());
            return r;
        });

        readiness.setCleaningDone(req.getCleaningDone());
        readiness.setSterilityDone(req.getSterilityDone());
        readiness.setEquipmentOk(req.getEquipmentOk());
        readiness.setStatus(req.getStatus());

        if ("READY".equalsIgnoreCase(req.getStatus())) {
            readiness.setVerifiedBy(securityHelper.getCurrentUserEmail());
            readiness.setVerifiedAt(LocalDateTime.now());
        } else {
            readiness.setVerifiedBy(null);
            readiness.setVerifiedAt(null);
        }

        OtReadiness saved = readinessRepository.save(readiness);
        audit("OT_READINESS_SAVED", "Readiness updated for room " + saved.getOtRoom() + " on " + saved.getReadinessDate() + " to " + saved.getStatus(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }
}

