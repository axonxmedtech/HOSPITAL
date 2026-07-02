package com.hms.controller.hospital;

import com.hms.entity.IncidentReport;
import com.hms.service.hospital.IncidentReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/hospital/quality/incidents")
public class IncidentReportController {

    @Autowired
    private IncidentReportService incidentReportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getIncidentReports() {
        return ResponseEntity.ok(incidentReportService.getIncidentReports());
    }

    @PostMapping("/{id}/investigate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateInvestigation(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        String notes = payload.get("notes");
        IncidentReport updated = incidentReportService.updateInvestigation(id, status, notes);
        return ResponseEntity.ok(updated);
    }
}
