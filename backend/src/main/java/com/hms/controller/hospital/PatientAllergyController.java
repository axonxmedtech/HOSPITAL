package com.hms.controller.hospital;

import com.hms.entity.PatientAllergy;
import com.hms.service.hospital.PatientAllergyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hospital/patients/{patientId}/allergies")
public class PatientAllergyController {

    @Autowired private PatientAllergyService patientAllergyService;

    @GetMapping
    public ResponseEntity<List<PatientAllergy>> getPatientAllergies(@PathVariable Long patientId) {
        return ResponseEntity.ok(patientAllergyService.getPatientAllergies(patientId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addAllergy(@PathVariable Long patientId, @RequestBody Map<String, Object> body) {
        try {
            Long allergyMasterId = Long.valueOf(body.get("allergyMasterId").toString());
            String severity = body.getOrDefault("severity", "UNKNOWN").toString();
            String notes = body.containsKey("notes") ? body.get("notes").toString() : null;
            PatientAllergy result = patientAllergyService.addAllergy(patientId, allergyMasterId, severity, notes);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{allergyId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<Void> removeAllergy(@PathVariable Long patientId, @PathVariable Long allergyId) {
        patientAllergyService.removeAllergy(patientId, allergyId);
        return ResponseEntity.noContent().build();
    }
}
