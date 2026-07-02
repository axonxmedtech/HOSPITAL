package com.hms.controller.hospital;

import com.hms.entity.PatientReferral;
import com.hms.repository.PatientReferralRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.entity.IpdAdmission;
import com.hms.exception.UnauthorizedException;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ipd/{admissionId}/referrals")
public class PatientReferralController {

    @Autowired
    private PatientReferralRepository referralRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> raiseReferral(@PathVariable Long admissionId,
                                           @RequestBody PatientReferral referral) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("Admission not found"));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        referral.setHospitalId(hospitalId);
        referral.setPatientId(admission.getPatientId());
        referral.setAdmissionId(admissionId);
        referral.setStatus("REQUESTED");
        referral.setRequestedAt(LocalDateTime.now());
        
        return ResponseEntity.ok(referralRepository.save(referral));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getReferrals(@PathVariable Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new RuntimeException("Admission not found"));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        return ResponseEntity.ok(referralRepository.findByHospitalIdAndAdmissionId(hospitalId, admissionId));
    }

    @PutMapping("/{referralId}/respond")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> respondToReferral(@PathVariable Long admissionId,
                                               @PathVariable Long referralId,
                                               @RequestBody PatientReferral responseBody) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        PatientReferral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));
        if (!referral.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        referral.setStatus(responseBody.getStatus() != null ? responseBody.getStatus() : "COMPLETED");
        referral.setRespondedBy(responseBody.getRespondedBy());
        referral.setResponseNote(responseBody.getResponseNote());

        return ResponseEntity.ok(referralRepository.save(referral));
    }
}
