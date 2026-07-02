package com.hms.controller.hospital;

import com.hms.dto.LabOrderRequest;
import com.hms.dto.LabResultRequest;
import com.hms.service.hospital.LabWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * LabController — REST API for the lab order workflow.
 *
 * Endpoint summary:
 *   POST   /hospital/lab/orders                        → place order (DOCTOR, HOSPITAL_ADMIN)
 *   GET    /hospital/lab/orders                        → list orders with filters (DOCTOR, NURSE, LAB_TECHNICIAN, HOSPITAL_ADMIN)
 *   GET    /hospital/lab/orders/{publicId}             → single order + result
 *   PUT    /hospital/lab/orders/{publicId}/collect-sample  → ORDERED → SAMPLE_COLLECTED (LAB_TECHNICIAN, HOSPITAL_ADMIN)
 *   POST   /hospital/lab/orders/{publicId}/result      → SAMPLE_COLLECTED → COMPLETED + create LabResult
 *   PUT    /hospital/lab/orders/{publicId}/cancel      → cancel order (DOCTOR, HOSPITAL_ADMIN)
 */
@RestController
@RequestMapping("/hospital/lab")
public class LabController {

    @Autowired private LabWorkflowService labWorkflowService;

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> placeOrder(@RequestBody LabOrderRequest req) {
        try {
            return ResponseEntity.ok(labWorkflowService.placeOrder(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ipdAdmissionId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(labWorkflowService.getOrders(status, ipdAdmissionId, patientId, pageable));
    }

    @GetMapping("/orders/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.getOrder(publicId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/collect-sample")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> collectSample(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.collectSample(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orders/{publicId}/result")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> enterResult(
            @PathVariable String publicId,
            @RequestBody LabResultRequest req) {
        try {
            return ResponseEntity.ok(labWorkflowService.enterResult(publicId, req));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.cancelOrder(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** BR-4: pathologist sign-off. Role check here is coarse (DOCTOR); the service enforces the actual is_pathologist flag. */
    @PutMapping("/orders/{publicId}/verify")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifyResult(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.verifyResult(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/release")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> releaseResult(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.releaseResult(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
