package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.entity.NursingProgressNote;
import com.hms.entity.NursingProcedure;
import com.hms.entity.ShiftHandover;
import com.hms.service.hospital.NursingProgressService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NursingProgressController - REST Controller managing daily per-shift nursing care notes,
 * non-medication clinical procedures, and shift handovers.
 *
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/nursing")
public class NursingProgressController {

    @Autowired
    private NursingProgressService progressService;

    /**
     * Files a new per-shift nursing care note (initially in DRAFT status). NURSE only.
     */
    @PostMapping("/progress")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<NursingProgressNote>> createNote(
            @Valid @RequestBody NursingProgressNoteCreateRequest request) {
        NursingProgressNote note = progressService.createProgressNote(
                request.getAdmissionId(),
                request.getShift(),
                request.getGeneralCondition(),
                request.getPainScore(),
                request.getRemarks(),
                request.getDoctorNotified(),
                request.getDoctorName(),
                request.getDoctorAdvice(),
                request.getPatientResponse()
        );
        return ResponseEntity.ok(ApiResponse.ok("Progress note filed successfully", note));
    }

    /**
     * Updates details of a draft progress note. NURSE only.
     */
    @PutMapping("/progress/{id}")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<NursingProgressNote>> updateNote(
            @PathVariable Long id,
            @RequestBody NursingProgressNoteUpdateRequest request) {
        NursingProgressNote note = progressService.updateProgressNote(
                id,
                request.getGeneralCondition(),
                request.getPainScore(),
                request.getRemarks(),
                request.getDoctorNotified(),
                request.getDoctorName(),
                request.getDoctorAdvice(),
                request.getPatientResponse(),
                request.getStatus()
        );
        return ResponseEntity.ok(ApiResponse.ok("Progress note updated successfully", note));
    }

    /**
     * Retrieves all care progress logs filed under a patient ID.
     */
    @GetMapping("/progress/patient/{patientId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<NursingProgressNote>>> getNotesByPatient(
            @PathVariable Long patientId) {
        List<NursingProgressNote> list = progressService.getNotesForPatient(patientId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Records a non-medication nursing procedure performed during the shift. NURSE only.
     */
    @PostMapping("/procedure")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<NursingProcedure>> recordProcedure(
            @Valid @RequestBody NursingProcedureRequest request) {
        NursingProcedure procedure = progressService.recordProcedure(
                request.getProgressNoteId(),
                request.getProcedureName(),
                request.getRemarks()
        );
        return ResponseEntity.ok(ApiResponse.ok("Procedure recorded successfully", procedure));
    }

    /**
     * Submits shift handovers to propagate clinical continuity notes. NURSE only.
     */
    @PostMapping("/handover")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<ApiResponse<ShiftHandover>> recordHandover(
            @Valid @RequestBody ShiftHandoverRequest request) {
        ShiftHandover handover = progressService.recordHandover(
                request.getAdmissionId(),
                request.getShift(),
                request.getIncomingNurseId(),
                request.getPendingTasks(),
                request.getCriticalAlerts(),
                request.getMedsDue(),
                request.getInvestigationsPending(),
                request.getDoctorReviewPending()
        );
        return ResponseEntity.ok(ApiResponse.ok("Shift handover processed successfully", handover));
    }
}
