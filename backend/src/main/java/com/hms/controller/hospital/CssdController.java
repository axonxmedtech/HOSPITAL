package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.CssdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * CSSD sterile instrument lifecycle (Form 35). Cycle verification (sterility release) is
 * restricted to Hospital Admin, mirroring the supervisor-only release gate in the blueprint;
 * hands-on tray operations use the existing NURSE role until a dedicated CSSD role is wired
 * into login/JWT issuance.
 */
@RestController
@RequestMapping("/hospital/cssd")
public class CssdController {

    @Autowired
    private CssdService cssdService;

    @PostMapping("/trays")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> registerTray(@RequestBody CssdTrayRegisterRequest request) {
        try {
            return ResponseEntity.ok(cssdService.registerTray(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/trays")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getTrays() {
        return ResponseEntity.ok(cssdService.getTrays());
    }

    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> returnTray(@RequestBody CssdReturnRequest request) {
        try {
            return ResponseEntity.ok(cssdService.returnTray(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/cycle/start")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> startCycle(@RequestBody CssdCycleStartRequest request) {
        try {
            return ResponseEntity.ok(cssdService.startCycle(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/cycle/verify/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifyCycle(@PathVariable Long id, @RequestBody CssdCycleVerifyRequest request) {
        try {
            return ResponseEntity.ok(cssdService.verifyCycle(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/cycles")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getCycles() {
        return ResponseEntity.ok(cssdService.getCycles());
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> issueTray(@RequestBody CssdIssueRequest request) {
        try {
            return ResponseEntity.ok(cssdService.issueTray(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/issues")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> getIssues() {
        return ResponseEntity.ok(cssdService.getIssues());
    }
}
