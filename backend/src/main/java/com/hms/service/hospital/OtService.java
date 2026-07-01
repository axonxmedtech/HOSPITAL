package com.hms.service.hospital;

import com.hms.dto.AnaesthesiaRecordRequest;
import com.hms.dto.OperationRecordRequest;
import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.dto.PacuRecordRequest;
import com.hms.entity.*;
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

        booking.setStatus(status);
        OtBooking saved = bookingRepository.save(booking);

        audit("OT_SURGERY_STATUS_UPDATED", "Surgery " + bookingId + " status changed to " + status, hospitalId);
        broadcast(hospitalId);

        return saved;
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

            // Auto-advance status to IN_PROGRESS when time-out is signed off
            booking.setStatus("IN_PROGRESS");
            bookingRepository.save(booking);
        } else if ("SIGN_OUT".equalsIgnoreCase(phase)) {
            if (!checklist.isTimeOutCompleted()) {
                throw new IllegalStateException("Cannot sign Sign Out before Time Out is completed.");
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
        OperationRecord record = operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Operation record not found for booking: " + bookingId));

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
