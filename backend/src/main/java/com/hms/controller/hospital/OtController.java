package com.hms.controller.hospital;

import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.service.hospital.OtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ipd/{admissionId}/ot/bookings")
public class OtController {

    @Autowired
    private OtService otService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getBookings(@PathVariable Long admissionId) {
        try {
            return ResponseEntity.ok(otService.getBookingsForAdmission(admissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> scheduleBooking(@PathVariable Long admissionId,
                                             @RequestBody OtBookingRequest request) {
        try {
            return ResponseEntity.ok(otService.scheduleBooking(admissionId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{bookingId}/checklist")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getChecklist(@PathVariable Long admissionId,
                                          @PathVariable Long bookingId) {
        try {
            return ResponseEntity.ok(otService.getChecklist(bookingId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long admissionId,
                                          @PathVariable Long bookingId,
                                          @RequestParam String status) {
        try {
            return ResponseEntity.ok(otService.updateStatus(bookingId, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{bookingId}/checklist")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> signChecklist(@PathVariable Long admissionId,
                                           @PathVariable Long bookingId,
                                           @RequestBody OtChecklistRequest request) {
        try {
            return ResponseEntity.ok(otService.signChecklist(bookingId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
