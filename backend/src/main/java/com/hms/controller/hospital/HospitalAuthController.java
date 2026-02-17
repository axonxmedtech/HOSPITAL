package com.hms.controller.hospital;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.service.hospital.HospitalAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HospitalAuthController - REST controller for Hospital user authentication
 * 
 * This controller handles Hospital user login at /login.
 * Used by Hospital Admin and Doctor users.
 * This is separate from Super Admin login (/platform/login).
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class HospitalAuthController {

    @Autowired
    private HospitalAuthService authService;

    /**
     * Hospital user login endpoint
     * Used by Hospital Admin and Doctor
     * 
     * @param request LoginRequest containing email and password
     * @return LoginResponse with JWT token and user details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get current user profile (Real-time sync)
     */
    @GetMapping("/auth/me")
    public ResponseEntity<?> getProfile(java.security.Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            LoginResponse response = authService.getProfile(principal.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    /**
     * Get hospital fees for the authenticated user's hospital
     */
    @GetMapping("/hospital/settings/fees")
    public ResponseEntity<?> getHospitalFees(java.security.Principal principal) {
        try {
            if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
            com.hms.dto.HospitalFeesDTO dto = authService.getHospitalFees(principal.getName());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    /**
     * Update hospital fees (consultation and case paper). Only Hospital Admin.
     */
    @PutMapping("/hospital/settings/fees")
    public ResponseEntity<?> updateHospitalFees(java.security.Principal principal, @RequestBody com.hms.dto.HospitalFeesDTO fees) {
        try {
            if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
            com.hms.dto.HospitalFeesDTO updated = authService.updateHospitalFees(principal.getName(), fees);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}
