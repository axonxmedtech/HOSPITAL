package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.CdssEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/cdss")
public class CdssController {

    @Autowired private CdssEvaluationService cdssService;
    @Autowired private SecurityContextHelper securityHelper;

    @PostMapping("/check-prescription")
    @PreAuthorize("hasAnyRole('DOCTOR','HOSPITAL_ADMIN')")
    public ResponseEntity<List<CdssAlertDTO>> checkPrescription(
            @RequestBody CdssCheckRequest req) {
        List<CdssAlertDTO> alerts = cdssService.evaluatePrescription(
                req.getPatientId(), req.getMedicineName(), req.getIpdAdmissionId());
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/acknowledge")
    @PreAuthorize("hasAnyRole('DOCTOR','HOSPITAL_ADMIN')")
    public ResponseEntity<Void> acknowledge(@RequestBody AcknowledgeRequest req) {
        cdssService.logAcknowledgement(
                req.getPatientId(), req.getIpdAdmissionId(),
                req.getAlerts(), req.getOverrideReason());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ews/{ipdAdmissionId}")
    public ResponseEntity<EwsResultDTO> getEws(@PathVariable Long ipdAdmissionId) {
        return ResponseEntity.ok(cdssService.calculateEws(ipdAdmissionId));
    }

    @GetMapping("/smart-summary/{ipdAdmissionId}")
    public ResponseEntity<SmartSummaryDTO> getSmartSummary(@PathVariable Long ipdAdmissionId) {
        return ResponseEntity.ok(cdssService.getSmartSummary(ipdAdmissionId));
    }

    @PostMapping("/seed-interactions")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<String> seedInteractions() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        cdssService.seedDrugInteractions(hospitalId);
        return ResponseEntity.ok("Drug interactions seeded successfully.");
    }
}
