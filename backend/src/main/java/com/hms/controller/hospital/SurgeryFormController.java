package com.hms.controller.hospital;

import com.hms.dto.SaveSurgeryFormRequest;
import com.hms.security.RequireModule;
import com.hms.service.hospital.SurgeryFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SurgeryFormController - OT/NABH surgery forms (OT module, Phase 2).
 * HOSPITAL tenant only, OT-gated. Nurse fills/saves; reads open to
 * nurse/doctor/admin.
 */
@RestController
@RequestMapping("/hospital/surgery-forms")
@RequireModule("OT")
public class SurgeryFormController {

    @Autowired
    private SurgeryFormService service;

    @PostMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<?> save(@RequestBody SaveSurgeryFormRequest req) {
        return ResponseEntity.ok(service.save(req));
    }

    @GetMapping("/admission/{admissionId}/{formType}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> get(@PathVariable Long admissionId, @PathVariable String formType) {
        return ResponseEntity.ok(service.get(admissionId, formType));
    }

    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> listSaved(@PathVariable Long admissionId) {
        return ResponseEntity.ok(service.listSavedTypes(admissionId));
    }
}
