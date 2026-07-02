package com.hms.controller.portal;

import com.hms.security.SecurityContextHelper;
import com.hms.service.portal.PatientPortalDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Patient portal dashboard/record endpoints (Form 40 phase 1). Every method resolves the
 * caller's identity via {@link SecurityContextHelper} — the JWT's {@code userId} claim is
 * the {@code patient_portal_user.id}, never a client-supplied patient ID.
 */
@RestController
@RequestMapping("/hospital/portal")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    @Autowired private PatientPortalDashboardService dashboardService;
    @Autowired private SecurityContextHelper securityHelper;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getAppointments() {
        return ResponseEntity.ok(dashboardService.getAppointments(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> getReports() {
        return ResponseEntity.ok(dashboardService.getReports(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<?> getPrescriptions() {
        return ResponseEntity.ok(dashboardService.getPrescriptions(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/billing")
    public ResponseEntity<?> getBilling() {
        return ResponseEntity.ok(dashboardService.getBilling(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }
}
