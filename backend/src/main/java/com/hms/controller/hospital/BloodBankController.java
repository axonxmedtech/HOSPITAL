package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.BloodBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Blood Bank / BBTMS (Form 38). */
@RestController
@RequestMapping("/hospital/blood-bank")
public class BloodBankController {

    @Autowired
    private BloodBankService bloodBankService;

    @PostMapping("/donors")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> registerDonor(@RequestBody BloodDonorRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.registerDonor(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/donors")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getDonors() {
        return ResponseEntity.ok(bloodBankService.getDonors());
    }

    @PostMapping("/units")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> addUnit(@RequestBody BloodUnitRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.addUnit(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/units")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getAvailableUnits(
            @RequestParam(required = false) String bloodGroup,
            @RequestParam(required = false) String rhType) {
        return ResponseEntity.ok(bloodBankService.getAvailableUnits(bloodGroup, rhType));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> requestBlood(@RequestBody BloodRequestRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.requestBlood(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getRequests() {
        return ResponseEntity.ok(bloodBankService.getRequests());
    }

    @PostMapping("/cross-match")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> performCrossMatch(@RequestBody CrossMatchRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.performCrossMatch(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/units/{bloodUnitId}/issue")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> issueUnit(@PathVariable Long bloodUnitId, @RequestParam Long patientId) {
        try {
            return ResponseEntity.ok(bloodBankService.issueUnit(bloodUnitId, patientId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/transfusions")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> startTransfusion(@RequestBody TransfusionRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.startTransfusion(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/transfusions/{recordId}/complete")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> completeTransfusion(@PathVariable Long recordId, @RequestBody TransfusionCompletionRequest request) {
        try {
            return ResponseEntity.ok(bloodBankService.completeTransfusion(recordId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/transfusions/patient/{patientId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getPatientTransfusions(@PathVariable Long patientId) {
        return ResponseEntity.ok(bloodBankService.getPatientTransfusions(patientId));
    }
}
