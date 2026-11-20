package com.hms.controller.hospital;

import jakarta.validation.Valid;

import com.hms.dto.MedicationAdminRequest;
import com.hms.service.hospital.MedicationAdministrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * MedicationAdminController - Medication Administration Record. Recording a dose is open to the
 * roles the hospital allows in Settings (all clinical roles by default); nurses stay bound to
 * their ward / assignment. A GIVEN dose comes out of stock and onto the IPD bill.
 */
@RestController
@RequestMapping("/hospital/nurse/medication")
public class MedicationAdminController {

    @Autowired
    private MedicationAdministrationService medicationService;

    @Autowired
    private com.hms.service.hospital.MedicationStockService medicationStock;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @GetMapping("/admission/{admissionId}/prescriptions")
    @PreAuthorize("hasAnyRole('NURSE','NURSE_INCHARGE','DOCTOR','HOSPITAL_ADMIN','RECEPTIONIST')")
    public ResponseEntity<?> getActivePrescriptions(@PathVariable Long admissionId) {
        return ResponseEntity.ok(medicationService.getActivePrescriptions(admissionId));
    }

    /** Full medication chart: all orders (active/stopped/completed) + reminders. */
    @GetMapping("/admission/{admissionId}/chart")
    @PreAuthorize("hasAnyRole('NURSE','NURSE_INCHARGE','DOCTOR','HOSPITAL_ADMIN','RECEPTIONIST')")
    public ResponseEntity<?> getMedicationChart(@PathVariable Long admissionId) {
        return ResponseEntity.ok(medicationService.getMedicationChart(admissionId));
    }

    private static final String CLINICAL = "hasAnyRole('NURSE','NURSE_INCHARGE','DOCTOR','HOSPITAL_ADMIN','RECEPTIONIST')";

    /** Record a dose. Which roles may is the hospital's choice (see /access); checked in the service. */
    @PostMapping
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> record(@Valid @RequestBody MedicationAdminRequest req) {
        return ResponseEntity.ok(medicationService.record(req));
    }

    /** Undo an entry made by mistake: stock back, charge off the bill. Body: {"reason": "..."}. */
    @PostMapping("/{publicId}/undo")
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> undo(@PathVariable String publicId,
                                  @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(medicationService.undo(publicId, body == null ? null : body.get("reason")));
    }

    /** Give something that was not ordered (an extra bottle, a dressing). */
    @PostMapping("/extra")
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> giveExtra(@Valid @RequestBody com.hms.dto.GiveExtraItemRequest req) {
        return ResponseEntity.ok(medicationService.giveExtra(req));
    }

    /** Whether the caller may record doses, and which roles the hospital allows. */
    @GetMapping("/access")
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> access() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(java.util.Map.of(
                "canRecord", medicationService.mayRecord(hospitalId),
                "roles", medicationService.recordingRoles(hospitalId),
                "allRoles", MedicationAdministrationService.RECORDING_ROLES));
    }

    /** Admin: choose who may record doses. Body: {"roles": ["NURSE", "DOCTOR", ...]}. */
    @PutMapping("/access")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> setAccess(@RequestBody java.util.Map<String, java.util.List<String>> body) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        medicationService.setRecordingRoles(hospitalId, body == null ? null : body.get("roles"));
        return access();
    }

    /** Medicines and hospital items with stock and low-stock flags, for ordering and giving. */
    @GetMapping("/stock/search")
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> searchStock(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(medicationStock.search(q));
    }

    /** Everything at or below its reorder level, in both inventories. */
    @GetMapping("/stock/low")
    @PreAuthorize(CLINICAL)
    public ResponseEntity<?> lowStock() {
        return ResponseEntity.ok(medicationStock.lowStock());
    }

    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE','NURSE_INCHARGE','DOCTOR','HOSPITAL_ADMIN','RECEPTIONIST')")
    public ResponseEntity<?> getByAdmission(@PathVariable Long admissionId) {
        return ResponseEntity.ok(medicationService.getByAdmission(admissionId));
    }
}
