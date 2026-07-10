package com.hms.controller.hospital;

import com.hms.dto.CreateSurgeryRequest;
import com.hms.dto.ScheduleSurgeryRequest;
import com.hms.entity.HospitalType;
import com.hms.security.RequireModule;
import com.hms.security.TenantType;
import com.hms.service.hospital.SurgeryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SurgeryController - Operation Theatre workflow (OT module, Phase 2).
 * HOSPITAL tenant only, OT-gated. Doctors create requests; reception
 * schedules/starts/completes/cancels; surgeons read their own board.
 */
@RestController
@RequestMapping("/hospital/surgeries")
@TenantType(HospitalType.HOSPITAL)
@RequireModule("OT")
public class SurgeryController {

    @Autowired
    private SurgeryService service;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> create(@RequestBody CreateSurgeryRequest req) {
        return ResponseEntity.ok(service.createRequest(req));
    }

    @GetMapping("/admission/{admissionId}/active")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> activeForAdmission(@PathVariable Long admissionId) {
        return ResponseEntity.ok(service.getActiveForAdmission(admissionId));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> requests() {
        return ResponseEntity.ok(service.listRequests());
    }

    @GetMapping("/board")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> board() {
        return ResponseEntity.ok(service.listBoard());
    }

    @GetMapping("/my-board")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> myBoard() {
        return ResponseEntity.ok(service.listMyBoard());
    }

    @GetMapping("/surgeons")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> surgeons() {
        return ResponseEntity.ok(service.listSurgeons());
    }

    @PostMapping("/{publicId}/schedule")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> schedule(@PathVariable String publicId, @RequestBody ScheduleSurgeryRequest req) {
        return ResponseEntity.ok(service.schedule(publicId, req));
    }

    @PostMapping("/{publicId}/start")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> start(@PathVariable String publicId) {
        return ResponseEntity.ok(service.start(publicId));
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> complete(@PathVariable String publicId) {
        return ResponseEntity.ok(service.complete(publicId));
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> cancel(@PathVariable String publicId) {
        service.cancel(publicId);
        return ResponseEntity.ok(Map.of("message", "Surgery cancelled"));
    }
}
