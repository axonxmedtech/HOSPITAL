package com.hms.controller.hospital;

import com.hms.dto.LabTechnicianRequest;
import com.hms.service.hospital.LabTechnicianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * LabTechnicianController — Admin CRUD for lab technician accounts.
 * All endpoints require HOSPITAL_ADMIN role.
 * Base path: /hospital/lab-technicians
 */
@RestController
@RequestMapping("/hospital/lab-technicians")
public class LabTechnicianController {

    @Autowired private LabTechnicianService labTechnicianService;

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> create(@RequestBody LabTechnicianRequest req) {
        try {
            return ResponseEntity.ok(labTechnicianService.create(
                    req.getName(), req.getEmail(), req.getPassword(), req.getPhone()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(labTechnicianService.list(search, pageable));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable String publicId,
            @RequestBody LabTechnicianRequest req) {
        try {
            return ResponseEntity.ok(labTechnicianService.update(publicId, req.getName(), req.getPhone()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable String publicId) {
        try {
            labTechnicianService.deactivate(publicId);
            return ResponseEntity.ok("{\"message\":\"Lab technician deactivated\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
