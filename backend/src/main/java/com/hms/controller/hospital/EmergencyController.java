package com.hms.controller.hospital;

import com.hms.dto.EmergencyVisitRequest;
import com.hms.service.hospital.EmergencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Emergency Information System (Form 12) — ER priority board + treat-first flow. */
@RestController
@RequestMapping("/api/emergency/visits")
public class EmergencyController {

    @Autowired
    private EmergencyService emergencyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getActiveVisits() {
        try {
            return ResponseEntity.ok(emergencyService.getActiveVisits());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> registerVisit(@RequestBody EmergencyVisitRequest request) {
        try {
            return ResponseEntity.ok(emergencyService.registerVisit(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{visitId}/triage")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> triage(@PathVariable Long visitId,
                                    @RequestBody EmergencyVisitRequest request) {
        try {
            return ResponseEntity.ok(emergencyService.triage(visitId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{visitId}/assess")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> assess(@PathVariable Long visitId,
                                    @RequestBody EmergencyVisitRequest request) {
        try {
            return ResponseEntity.ok(emergencyService.assess(visitId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{visitId}/dispose")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> dispose(@PathVariable Long visitId,
                                     @RequestBody EmergencyVisitRequest request) {
        try {
            return ResponseEntity.ok(emergencyService.dispose(visitId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
