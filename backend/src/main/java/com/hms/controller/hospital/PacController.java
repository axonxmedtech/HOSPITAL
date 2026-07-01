package com.hms.controller.hospital;

import com.hms.dto.PacRequest;
import com.hms.service.hospital.OtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Pre-Anaesthesia Assessment (Form 15) — admission-scoped, gates OT scheduling. */
@RestController
@RequestMapping("/api/ipd/{admissionId}/ot/pac")
public class PacController {

    @Autowired
    private OtService otService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPac(@PathVariable Long admissionId) {
        try {
            return ResponseEntity.ok(otService.getPac(admissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> savePac(@PathVariable Long admissionId,
                                     @RequestBody PacRequest request) {
        try {
            return ResponseEntity.ok(otService.savePac(admissionId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> approvePac(@PathVariable Long admissionId) {
        try {
            return ResponseEntity.ok(otService.approvePac(admissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
