package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.ClinicalAssessmentFinalizeRequest;
import com.hms.dto.ClinicalAssessmentUpdateRequest;
import com.hms.entity.*;
import com.hms.service.hospital.ClinicalAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ClinicalAssessmentController - REST Controller managing Clinical Initial Assessments
 * and EMR longitudinal history views.
 *
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/clinical-assessments")
public class ClinicalAssessmentController {

    @Autowired
    private ClinicalAssessmentService clinicalAssessmentService;

    /**
     * Initializes a new clinical assessment draft or returns an existing active draft.
     */
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalAssessment>> createDraft(@RequestParam Long admissionId) {
        ClinicalAssessment draft = clinicalAssessmentService.createDraft(admissionId);
        return ResponseEntity.ok(ApiResponse.ok("Draft clinical assessment created successfully", draft));
    }

    /**
     * Updates content text fields of a draft assessment.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalAssessment>> updateDraft(
            @PathVariable Long id,
            @RequestBody ClinicalAssessmentUpdateRequest request) {
        ClinicalAssessment updated = clinicalAssessmentService.updateDraft(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Assessment draft updated successfully", updated));
    }

    /**
     * Finalizes the assessment, locking it to read-only and spawning downstream orders.
     */
    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalAssessment>> finalizeAssessment(
            @PathVariable Long id,
            @RequestBody ClinicalAssessmentFinalizeRequest request) {
        ClinicalAssessment finalized = clinicalAssessmentService.finalizeAssessment(
                id,
                request.getMedicalHistory(),
                request.getSurgicalHistory(),
                request.getMedicationHistory(),
                request.getFamilyHistory(),
                request.getSocialHistory(),
                request.getDoctorOrders()
        );
        return ResponseEntity.ok(ApiResponse.ok("Assessment finalized and locked successfully", finalized));
    }

    /**
     * Spawns an amendment (new version) based on a prior finalized assessment.
     */
    @PostMapping("/{id}/amend")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalAssessment>> amendAssessment(
            @PathVariable Long id,
            @RequestBody ClinicalAssessmentUpdateRequest request) {
        ClinicalAssessment amendment = clinicalAssessmentService.amendAssessment(
                id,
                request.getChiefComplaint(),
                request.getHistoryPresentIllness(),
                request.getProvisionalDiagnosis(),
                request.getTreatmentPlan()
        );
        return ResponseEntity.ok(ApiResponse.ok("Amendment drafted successfully", amendment));
    }

    /**
     * Fetches longitudinal diagnoses timelines for a patient.
     */
    @GetMapping("/patient/{patientId}/history")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PatientDiagnosis>>> getPatientHistory(@PathVariable Long patientId) {
        List<PatientDiagnosis> list = clinicalAssessmentService.getPatientDiagnoses(patientId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
