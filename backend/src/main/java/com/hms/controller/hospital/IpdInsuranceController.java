package com.hms.controller.hospital;

import com.hms.entity.IpdInsuranceClaim;
import com.hms.security.RequireModule;
import com.hms.service.hospital.IpdInsuranceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Insurance / TPA claims of inpatients. Hospital tenants only; the service checks the desk role. */
@RestController
@RequireModule("IPD")
@RequestMapping("/hospital/ipd")
public class IpdInsuranceController {

    private final IpdInsuranceService service;

    public IpdInsuranceController(IpdInsuranceService service) {
        this.service = service;
    }

    /** Claim details. */
    public record ClaimBody(String payerType, String insurerName, String tpaName, String policyNumber, String memberId,
                            String claimNumber, BigDecimal requestedAmount, String note) {
        IpdInsuranceService.ClaimDetails details() {
            return new IpdInsuranceService.ClaimDetails(payerType, insurerName, tpaName, policyNumber, memberId,
                    claimNumber, requestedAmount, note);
        }
    }

    /** A step in the claim. */
    public record StepBody(String status, BigDecimal amount, String claimNumber, String note) {}

    /** The insurer's payment. */
    public record SettleBody(BigDecimal amount, BigDecimal deduction, String deductionReason, String reference) {}

    @GetMapping("/{id}/insurance")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<List<Map<String, Object>>> forAdmission(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.forAdmission(id));
    }

    @PostMapping("/{id}/insurance")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<IpdInsuranceClaim> open(@PathVariable("id") Long id, @RequestBody ClaimBody body) {
        return ResponseEntity.ok(service.open(id, body.details()));
    }

    @GetMapping("/insurance/open")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<List<Map<String, Object>>> openClaims() {
        return ResponseEntity.ok(service.openClaims());
    }

    @PutMapping("/insurance/{publicId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<IpdInsuranceClaim> update(@PathVariable("publicId") String publicId, @RequestBody ClaimBody body) {
        return ResponseEntity.ok(service.update(publicId, body.details()));
    }

    @PostMapping("/insurance/{publicId}/step")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<IpdInsuranceClaim> step(@PathVariable("publicId") String publicId, @RequestBody StepBody body) {
        return ResponseEntity.ok(service.advance(publicId, body.status(), body.amount(), body.claimNumber(), body.note()));
    }

    @PostMapping("/insurance/{publicId}/settle")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<IpdInsuranceClaim> settle(@PathVariable("publicId") String publicId, @RequestBody SettleBody body) {
        return ResponseEntity.ok(service.settle(publicId, body.amount(), body.deduction(), body.deductionReason(), body.reference()));
    }
}
