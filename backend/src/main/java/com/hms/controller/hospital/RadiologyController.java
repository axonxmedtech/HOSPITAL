package com.hms.controller.hospital;

import com.hms.dto.RadiologyOrderRequest;
import com.hms.dto.RadiologyResultRequest;
import com.hms.service.hospital.RadiologyWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/radiology")
public class RadiologyController {

    @Autowired private RadiologyWorkflowService radiologyWorkflowService;

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> placeOrder(@RequestBody RadiologyOrderRequest req) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.placeOrder(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RADIOLOGY_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ipdAdmissionId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(radiologyWorkflowService.getOrders(status, ipdAdmissionId, patientId, pageable));
    }

    @GetMapping("/orders/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RADIOLOGY_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.getOrder(publicId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/conduct-study")
    @PreAuthorize("hasAnyRole('RADIOLOGY_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> conductStudy(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.conductStudy(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orders/{publicId}/result")
    @PreAuthorize("hasAnyRole('RADIOLOGY_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> enterResult(
            @PathVariable String publicId,
            @RequestBody RadiologyResultRequest req) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.enterResult(publicId, req));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.cancelOrder(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** BR-4: radiologist sign-off. Role check here is coarse (DOCTOR); the service enforces the actual is_radiologist flag. */
    @PutMapping("/orders/{publicId}/verify")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifyResult(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.verifyResult(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/release")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> releaseResult(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(radiologyWorkflowService.releaseResult(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
