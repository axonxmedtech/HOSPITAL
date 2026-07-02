package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.HrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Human Resources & Workforce Management (Form 39). Onboarding, rostering, leave approval,
 * and payroll are Admin-only (mirroring the HR Executive/Manager gates in the blueprint,
 * until dedicated HR roles are wired into login); leave self-service is open to any
 * authenticated clinical/pharmacy staff role.
 */
@RestController
@RequestMapping("/hospital/hr")
public class HrController {

    @Autowired
    private HrService hrService;

    @PostMapping("/employee")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> onboardEmployee(@RequestBody EmployeeOnboardRequest request) {
        try {
            return ResponseEntity.ok(hrService.onboardEmployee(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/employee")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getEmployees() {
        return ResponseEntity.ok(hrService.getEmployees());
    }

    @PostMapping("/employee/{id}/exit")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> exitEmployee(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(hrService.exitEmployee(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/roster")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> createRosterSlot(@RequestBody ShiftRosterRequest request) {
        try {
            return ResponseEntity.ok(hrService.createRosterSlot(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/roster")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getRoster() {
        return ResponseEntity.ok(hrService.getRoster());
    }

    @PostMapping("/leave")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST')")
    public ResponseEntity<?> requestLeave(@RequestBody LeaveRequestSubmitRequest request) {
        try {
            return ResponseEntity.ok(hrService.requestLeave(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/leave")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getLeaveRequests() {
        return ResponseEntity.ok(hrService.getLeaveRequests());
    }

    @PostMapping("/leave/approve/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> approveLeave(@PathVariable Long id, @RequestBody LeaveApprovalRequest request) {
        try {
            return ResponseEntity.ok(hrService.approveLeave(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/payroll/process")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> processPayroll(@RequestBody PayrollProcessRequest request) {
        try {
            return ResponseEntity.ok(hrService.processPayroll(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/payroll")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPayroll() {
        return ResponseEntity.ok(hrService.getPayroll());
    }
}
