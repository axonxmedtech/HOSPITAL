package com.hms.controller.hospital;

import com.hms.exception.UnauthorizedException;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.security.RequireModule;
import com.hms.service.hospital.HospitalAuthService;
import com.hms.service.platform.PlatformPlanService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization note (S-SEC-2): every settings mutation below is also refused inside
 * {@code HospitalAuthService} by a role comparison. The {@code @PreAuthorize} annotations are
 * deliberate duplication: they put the rule at the edge, where it is visible next to the route
 * and enforced before the request reaches a service, rather than depending on a string
 * comparison several layers in. The two read endpoints had no role check anywhere.
 */
@RestController
public class HospitalAuthController {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(HospitalAuthController.class);

    @Autowired
    private HospitalAuthService authService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.UserRepository userRepository;

    @Autowired
    private PlatformPlanService planService;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> getProfile(java.security.Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        LoginResponse response = authService.getProfile(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/auth/profile")
    public ResponseEntity<?> updateProfile(java.security.Principal principal, @RequestBody com.hms.dto.ProfileUpdateRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        LoginResponse response = authService.updateProfile(principal.getName(), request);

        // Editing a profile changed the name/phone/specialization the dashboards render, so
        // tell the hospital's clients to re-read it. SETTINGS_UPDATED makes each client
        // re-fetch its OWN profile (so the editor's own header updates without a reload);
        // REFRESH_DATA refreshes the lists this person appears in (e.g. the admin's Doctors
        // table). Best-effort — a socket failure must not fail the save.
        try {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"SETTINGS_UPDATED\"}");
                webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
            });
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after profile update", e);
        }
        return ResponseEntity.ok(response);
    }

    // Read by whoever raises a bill: the consultation modal prefills from it, and both the
    // doctor and receptionist dashboards show it. Nurses, pharmacists and the OT incharge have
    // no screen that reads a fee.
    @GetMapping({"/hospital/settings/fees", "/clinic/settings/fees", "/pharmacy/settings/fees"})
    @RequireModule("BILLING")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST')")
    public ResponseEntity<?> getHospitalFees(java.security.Principal principal) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        com.hms.dto.HospitalFeesDTO dto = authService.getHospitalFees(principal.getName());
        return ResponseEntity.ok(dto);
    }

    @PutMapping({"/hospital/settings/fees", "/clinic/settings/fees", "/pharmacy/settings/fees"})
    @RequireModule("BILLING")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateHospitalFees(java.security.Principal principal, @RequestBody com.hms.dto.HospitalFeesDTO fees) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        com.hms.dto.HospitalFeesDTO updated = authService.updateHospitalFees(principal.getName(), fees);
        userRepository.findByEmail(principal.getName()).ifPresent(user -> {
            webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"SETTINGS_UPDATED\"}");
        });
        return ResponseEntity.ok(updated);
    }

    // Every clinical chart panel asks this endpoint whether Separate Nurse Login is on, so the
    // readers are the roles that can open a chart: the nurse screens, and /ipd/:id, whose route
    // admits RECEPTIONIST, DOCTOR and HOSPITAL_ADMIN. Not the pharmacist or the OT incharge.
    @GetMapping({"/hospital/settings/operations", "/clinic/settings/operations", "/pharmacy/settings/operations"})
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<?> getOperationsSettings(java.security.Principal principal) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        return ResponseEntity.ok(authService.getHospitalOperationsSettings(principal.getName()));
    }

    @PutMapping({"/hospital/settings/operations", "/clinic/settings/operations", "/pharmacy/settings/operations"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateOperationsSettings(java.security.Principal principal, @RequestBody com.hms.dto.HospitalSettingDTO dto) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        com.hms.dto.HospitalSettingDTO updated = authService.updateHospitalOperationsSettings(principal.getName(), dto);
        userRepository.findByEmail(principal.getName()).ifPresent(user -> {
            webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"SETTINGS_UPDATED\"}");
        });
        return ResponseEntity.ok(updated);
    }

    /**
     * Update Print Settings (pages in the consultation print) and/or bill payment timing.
     * Body is a partial HospitalSettingDTO; null fields are left unchanged.
     */
    @PutMapping({"/hospital/settings/print-payment", "/clinic/settings/print-payment"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updatePrintAndPaymentSettings(java.security.Principal principal, @RequestBody com.hms.dto.HospitalSettingDTO dto) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        return ResponseEntity.ok(authService.updatePrintAndPaymentSettings(principal.getName(), dto));
    }

    /** Toggle the pharmacy barcode workflow. Body: { "barcodeEnabled": true|false }. */
    @PutMapping({"/hospital/settings/barcode", "/clinic/settings/barcode", "/pharmacy/settings/barcode"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateBarcodeSetting(java.security.Principal principal, @RequestBody java.util.Map<String, Boolean> body) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        Boolean enabled = body.get("barcodeEnabled");
        if (enabled == null) throw new IllegalArgumentException("barcodeEnabled is required");
        return ResponseEntity.ok(authService.updateBarcodeSetting(principal.getName(), enabled));
    }

    /** Toggle the separate Nurse Login page. Body: { "separateNurseLogin": true|false }. */
    @PutMapping({"/hospital/settings/nurse-login", "/clinic/settings/nurse-login", "/pharmacy/settings/nurse-login"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateSeparateNurseLoginSetting(java.security.Principal principal, @RequestBody java.util.Map<String, Boolean> body) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        Boolean enabled = body.get("separateNurseLogin");
        if (enabled == null) throw new IllegalArgumentException("separateNurseLogin is required");
        return ResponseEntity.ok(authService.updateSeparateNurseLoginSetting(principal.getName(), enabled));
    }

    /** Toggle the OT Incharge setting. Body: { "otInchargeEnabled": true|false }. */
    @PutMapping({"/hospital/settings/ot-incharge", "/clinic/settings/ot-incharge", "/pharmacy/settings/ot-incharge"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateOtInchargeSetting(java.security.Principal principal, @RequestBody java.util.Map<String, Boolean> body) {
        if (principal == null) throw new UnauthorizedException("Authentication required");
        Boolean enabled = body.get("otInchargeEnabled");
        if (enabled == null) throw new IllegalArgumentException("otInchargeEnabled is required");
        return ResponseEntity.ok(authService.updateOtInchargeSetting(principal.getName(), enabled));
    }

    @GetMapping({"/hospital/subscription", "/clinic/subscription", "/pharmacy/subscription"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getSubscriptionInfo() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            com.hms.dto.SubscriptionInfoDTO dto = planService.getSubscriptionInfo(hospitalId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return com.hms.util.ApiErrors.handle(e);
        }
    }
}
