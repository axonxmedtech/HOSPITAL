package com.hms.controller.hospital;

import jakarta.validation.Valid;

import com.hms.entity.AdmissionForm;
import com.hms.service.hospital.AdmissionFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AdmissionFormController - nurse fills the IPD admission form to complete an
 * admission (Phase 1 Nurse module). HOSPITAL-tenant only, NURSING-gated.
 * Reading the form is open to doctors/admins too — the clinical form panels use
 * it for the patient header — while filling/confirming stays a nurse / front-desk workflow (a doctor
 * fills it where the doctor is the front desk: Solo or Both reception mode).
 * Assignment scoping is enforced in the service.
 */
@RestController
@RequestMapping("/hospital/nurse/admission-form")
public class AdmissionFormController {

    @Autowired
    private AdmissionFormService admissionFormService;

    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE','NURSE_INCHARGE','DOCTOR','HOSPITAL_ADMIN','RECEPTIONIST')")
    public ResponseEntity<?> get(@PathVariable Long admissionId) {
        return ResponseEntity.ok(admissionFormService.getOrDraft(admissionId));
    }

    // Reception and admin fill it at the admission desk, where the form is commonly signed; the
    // ward nurse can still complete it. Tenant scope and assignment are enforced in the service.
    @PostMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE','RECEPTIONIST','HOSPITAL_ADMIN','DOCTOR')")
    public ResponseEntity<?> save(@PathVariable Long admissionId, @Valid @RequestBody AdmissionForm form) {
        assertDoctorIsTheDesk();
        return ResponseEntity.ok(admissionFormService.save(admissionId, form));
    }

    @PostMapping("/admission/{admissionId}/confirm")
    @PreAuthorize("hasAnyRole('NURSE','RECEPTIONIST','HOSPITAL_ADMIN','DOCTOR')")
    public ResponseEntity<?> markAdmitted(@PathVariable Long admissionId) {
        assertDoctorIsTheDesk();
        admissionFormService.markAdmitted(admissionId);
        return ResponseEntity.ok(java.util.Map.of("admissionConfirmed", true));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.service.hospital.HospitalStaffing hospitalStaffing;

    /** A doctor fills the form only where the doctor is also the front desk (Solo / Both mode). */
    private void assertDoctorIsTheDesk() {
        if ("DOCTOR".equals(securityHelper.getCurrentUserRole())
                && !hospitalStaffing.of(securityHelper.getCurrentHospitalId()).doctorActsAsReception()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "The admission form is completed at the front desk in this hospital");
        }
    }
}
