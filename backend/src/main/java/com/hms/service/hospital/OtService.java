package com.hms.service.hospital;

import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
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
