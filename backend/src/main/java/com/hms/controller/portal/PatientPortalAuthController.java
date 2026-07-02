package com.hms.controller.portal;

import com.hms.dto.PortalOtpRequestRequest;
import com.hms.dto.PortalOtpVerifyRequest;
import com.hms.service.portal.PatientPortalAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Patient portal OTP login (Form 40 phase 1) — public, unauthenticated by design. */
@RestController
@RequestMapping("/hospital/portal/otp")
public class PatientPortalAuthController {

    @Autowired
    private PatientPortalAuthService authService;

    @PostMapping("/request")
    public ResponseEntity<?> requestOtp(@RequestBody PortalOtpRequestRequest request) {
        try {
            if (request.getHospitalId() == null) {
                return ResponseEntity.badRequest().body("hospitalId is required");
            }
            authService.requestOtp(request.getHospitalId(), request);
            return ResponseEntity.ok(java.util.Map.of("message", "OTP sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody PortalOtpVerifyRequest request) {
        try {
            if (request.getHospitalId() == null) {
                return ResponseEntity.badRequest().body("hospitalId is required");
            }
            return ResponseEntity.ok(authService.verifyOtp(request.getHospitalId(), request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
