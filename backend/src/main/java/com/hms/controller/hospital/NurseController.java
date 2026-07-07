package com.hms.controller.hospital;

import com.hms.entity.User;
import com.hms.security.RequireModule;
import com.hms.service.hospital.NurseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * NurseController - Hospital Admin management of nurse staff (Phase 1).
 *
 * HOSPITAL-tenant only: mapped exclusively under /hospital/** (never /clinic or
 * /pharmacy) and gated by the NURSING module. Cloned from ReceptionistController.
 */
@RestController
@RequestMapping("/hospital/nurses")
@RequireModule("NURSING")
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class NurseController {

    @Autowired
    private NurseService nurseService;

    @PostMapping
    public ResponseEntity<?> createNurse(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String email = payload.get("email");
        String password = payload.get("password");

        if (name == null || email == null || password == null) {
            return ResponseEntity.badRequest().body("Name, Email, and Password are required");
        }
        if (password.trim().length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }

        User created = nurseService.createNurse(name, email, password,
                payload.get("phone"), payload.get("licenseNumber"), parseWardId(payload.get("wardId")));
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<?> getAllNurses(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(nurseService.getAllNurses(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getNurseById(@PathVariable String id) {
        return ResponseEntity.ok(nurseService.getNurseByPublicId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNurse(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Name is required");
        }
        return ResponseEntity.ok(nurseService.updateNurse(id, name, parseWardId(payload.get("wardId"))));
    }

    /** Parse an optional wardId string from the request payload. */
    private Long parseWardId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ward selected");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNurse(@PathVariable String id,
            @RequestParam(required = false) String reason) {
        nurseService.deleteNurse(id, reason);
        return ResponseEntity.ok("Nurse deleted successfully");
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetNursePassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }
        nurseService.resetNursePassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}
