package com.hms.controller.hospital;

import com.hms.dto.RadiologyTechnicianRequest;
import com.hms.service.hospital.RadiologyTechnicianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/radiology-technicians")
public class RadiologyTechnicianController {

    @Autowired private RadiologyTechnicianService radiologyTechnicianService;

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> create(@RequestBody RadiologyTechnicianRequest req) {
        try {
            return ResponseEntity.ok(radiologyTechnicianService.create(
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
        return ResponseEntity.ok(radiologyTechnicianService.list(search, pageable));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable String publicId,
            @RequestBody RadiologyTechnicianRequest req) {
        try {
            return ResponseEntity.ok(radiologyTechnicianService.update(publicId, req.getName(), req.getPhone()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable String publicId) {
        try {
            radiologyTechnicianService.deactivate(publicId);
            return ResponseEntity.ok("{\"message\":\"Radiology technician deactivated\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
