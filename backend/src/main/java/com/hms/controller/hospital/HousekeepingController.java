package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.HousekeepingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Housekeeping & Facility Management (Form 37). Hands-on cleaning/waste operations use the
 * existing NURSE role until a dedicated Housekeeper role is wired into login; verification
 * (bed release) and complaint engineer-side confirmation are Admin-only, mirroring the
 * supervisor/engineer gates in the blueprint.
 */
@RestController
@RequestMapping("/hospital/housekeeping")
public class HousekeepingController {

    @Autowired
    private HousekeepingService housekeepingService;

    @PostMapping("/task")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> createTask(@RequestBody CleaningTaskRequest request) {
        try {
            return ResponseEntity.ok(housekeepingService.createTask(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/task")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getTasks() {
        return ResponseEntity.ok(housekeepingService.getTasks());
    }

    @PostMapping("/complete/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> completeTask(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(housekeepingService.completeTask(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifyTask(@PathVariable Long id, @RequestBody TaskVerifyRequest request) {
        try {
            return ResponseEntity.ok(housekeepingService.verifyTask(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/waste")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> logWaste(@RequestBody WasteCollectionRequest request) {
        try {
            return ResponseEntity.ok(housekeepingService.logWaste(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/waste")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getWasteLog() {
        return ResponseEntity.ok(housekeepingService.getWasteLog());
    }

    @PostMapping("/complaint")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> openComplaint(@RequestBody FacilityComplaintRequest request) {
        try {
            return ResponseEntity.ok(housekeepingService.openComplaint(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/complaint")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getComplaints() {
        return ResponseEntity.ok(housekeepingService.getComplaints());
    }

    @PostMapping("/complaint/{id}/confirm")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> confirmComplaint(@PathVariable Long id, @RequestBody ComplaintConfirmRequest request) {
        try {
            return ResponseEntity.ok(housekeepingService.confirmComplaint(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
