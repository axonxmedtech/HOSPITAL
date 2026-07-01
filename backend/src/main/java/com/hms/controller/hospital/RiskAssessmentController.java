package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.RiskAssessmentCreateRequest;
import com.hms.dto.RiskAssessmentReviewRequest;
import com.hms.entity.PatientRiskAssessment;
import com.hms.service.hospital.RiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RiskAssessmentController - Endpoints for screening patient vulnerability risks
 * and managing clinical safety alerts.
 *
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/risk-assessments")
public class RiskAssessmentController {

    @Autowired
    private RiskAssessmentService riskService;

    /**
     * Evaluates inputs checklist, calculates risk levels, schedules safety protocols,
     * and broadcasts alerts. NURSE only.
     */
    @PostMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<PatientRiskAssessment>> evaluateRisk(
            @Valid @RequestBody RiskAssessmentCreateRequest request) {
        PatientRiskAssessment assessment = riskService.evaluateAndSaveRisk(
                request.getPatientId(),
                request.getAdmissionId(),
                request.getInputsJson(),
                request.getRemarks()
        );
        return ResponseEntity.ok(ApiResponse.ok("Risk assessment processed and scheduled successfully", assessment));
    }

    /**
     * Retrieves all vulnerability risk records filed under an admission.
     */
    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PatientRiskAssessment>>> getAssessmentsByAdmission(
            @PathVariable Long admissionId) {
        List<PatientRiskAssessment> list = riskService.getAssessmentsForAdmission(admissionId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Signed-off review notes for patient safety. DOCTOR only.
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<PatientRiskAssessment>> reviewRisk(
            @PathVariable Long id,
            @RequestBody RiskAssessmentReviewRequest request) {
        PatientRiskAssessment assessment = riskService.reviewRiskAssessment(id, request.getReviewRemarks());
        return ResponseEntity.ok(ApiResponse.ok("High-risk assessment review completed", assessment));
    }
}
