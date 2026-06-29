package com.hms.controller.hospital;

import com.hms.entity.NurseAssessment;
import com.hms.entity.VitalSigns;
import com.hms.service.hospital.NurseAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}")
public class NurseAssessmentController {

    @Autowired private NurseAssessmentService assessmentService;

    @PostMapping("/assessment")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createAssessment(@PathVariable Long admissionId,
                                               @RequestBody Map<String, Object> body) {
        try {
            NurseAssessment result = assessmentService.createAssessment(admissionId, body);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/assessment")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getAssessment(@PathVariable Long admissionId) {
        return ResponseEntity.ok(assessmentService.getAssessment(admissionId));
    }

    @PostMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> recordVitals(@PathVariable Long admissionId,
                                          @RequestBody Map<String, Object> body) {
        VitalSigns result = assessmentService.recordVitals(admissionId, body);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<List<VitalSigns>> getVitals(@PathVariable Long admissionId) {
        return ResponseEntity.ok(assessmentService.getVitals(admissionId));
    }
}
