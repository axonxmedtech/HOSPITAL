package com.hms.controller.hospital;

import com.hms.entity.AdmissionForm;
import com.hms.security.RequireModule;
import com.hms.service.hospital.AdmissionFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AdmissionFormController - nurse fills the IPD admission form to complete an
 * admission (Phase 1 Nurse module). HOSPITAL-tenant only, NURSING-gated,
 * nurse-only (assignment-gated in the service).
 */
@RestController
@RequestMapping("/hospital/nurse/admission-form")
@RequireModule("NURSING")
@PreAuthorize("hasRole('NURSE')")
public class AdmissionFormController {

    @Autowired
    private AdmissionFormService admissionFormService;

    @GetMapping("/admission/{admissionId}")
    public ResponseEntity<?> get(@PathVariable Long admissionId) {
        return ResponseEntity.ok(admissionFormService.getOrDraft(admissionId));
    }

    @PostMapping("/admission/{admissionId}")
    public ResponseEntity<?> save(@PathVariable Long admissionId, @RequestBody AdmissionForm form) {
        return ResponseEntity.ok(admissionFormService.save(admissionId, form));
    }

    @PostMapping("/admission/{admissionId}/confirm")
    public ResponseEntity<?> markAdmitted(@PathVariable Long admissionId) {
        admissionFormService.markAdmitted(admissionId);
        return ResponseEntity.ok(java.util.Map.of("admissionConfirmed", true));
    }
}
