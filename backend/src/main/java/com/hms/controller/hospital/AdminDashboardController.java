package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Hospital Administration & Executive/Clinical MIS Dashboard (Form 32). */
@RestController
@RequestMapping("/hospital/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService dashboardService;

    @GetMapping("/executive")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getExecutiveDashboard(@RequestParam(required = false) String timeframe) {
        try {
            return ResponseEntity.ok(dashboardService.getExecutiveDashboard(timeframe));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/clinical")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getClinicalDashboard() {
        return ResponseEntity.ok(dashboardService.getClinicalDashboard());
    }

    @PostMapping("/alert")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> createAlert(@RequestBody ExecutiveAlertRequest request) {
        try {
            return ResponseEntity.ok(dashboardService.createAlert(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/alert")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getAlerts() {
        return ResponseEntity.ok(dashboardService.getAlerts());
    }

    @PostMapping("/alert/acknowledge")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> acknowledgeAlert(@RequestBody AlertAcknowledgeRequest request) {
        try {
            return ResponseEntity.ok(dashboardService.acknowledgeAlert(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
