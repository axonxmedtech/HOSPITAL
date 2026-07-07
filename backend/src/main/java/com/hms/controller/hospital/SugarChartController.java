package com.hms.controller.hospital;

import com.hms.dto.SugarChartRequest;
import com.hms.security.RequireModule;
import com.hms.service.hospital.SugarChartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SugarChartController - blood-sugar chart (Phase 1 Nurse module). HOSPITAL
 * tenant only, NURSING-gated. Writes are nurse-only (assignment-gated in the
 * service); reads also open to doctors and admins.
 */
@RestController
@RequestMapping("/hospital/nurse/sugar-chart")
@RequireModule("NURSING")
public class SugarChartController {

    @Autowired
    private SugarChartService service;

    @PostMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<?> create(@RequestBody SugarChartRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getByAdmission(@PathVariable Long admissionId) {
        return ResponseEntity.ok(service.getByAdmission(admissionId));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<?> update(@PathVariable String publicId, @RequestBody SugarChartRequest req) {
        return ResponseEntity.ok(service.update(publicId, req));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<?> delete(@PathVariable String publicId) {
        service.softDelete(publicId);
        return ResponseEntity.ok(Map.of("message", "Entry deleted"));
    }
}
