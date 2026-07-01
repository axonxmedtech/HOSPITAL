package com.hms.controller.hospital;

import com.hms.dto.OtReadinessRequest;
import com.hms.service.hospital.OtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/hospital/ot")
public class OtRegisterController {

    @Autowired
    private OtService otService;

    @GetMapping("/register")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOtRegister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String room) {
        try {
            return ResponseEntity.ok(otService.getRegisterEntries(date, room));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getReadiness(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String room) {
        try {
            return ResponseEntity.ok(otService.getReadiness(date, room));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/readiness")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> saveReadiness(@RequestBody OtReadinessRequest request) {
        try {
            return ResponseEntity.ok(otService.saveReadiness(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
