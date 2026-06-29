package com.hms.controller.hospital;

import com.hms.entity.User;
import com.hms.service.hospital.NurseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/hospital/nurses")
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class NurseController {

    @Autowired
    private NurseService nurseService;

    @PostMapping
    public ResponseEntity<?> createNurse(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String email = payload.get("email");
        String password = payload.get("password");
        String phone = payload.get("phone");

        if (name == null || email == null || password == null) {
            return ResponseEntity.badRequest().body("Name, Email, and Password are required");
        }

        User created = nurseService.createNurse(name, email, password, phone);
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

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNurse(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String phone = payload.get("phone");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Name is required");
        }
        User updated = nurseService.updateNurse(id, name, phone);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNurse(@PathVariable String id) {
        nurseService.deleteNurse(id);
        return ResponseEntity.ok("Nurse deleted successfully");
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters");
        }
        nurseService.resetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PostMapping("/{id}/assign-ward")
    public ResponseEntity<?> assignWard(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object wardIdObj = body.get("wardId");
        if (wardIdObj == null) {
            return ResponseEntity.badRequest().body("wardId is required");
        }
        Long wardId;
        try {
            wardId = Long.valueOf(wardIdObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("wardId must be a valid number");
        }
        nurseService.assignWard(id, wardId);
        return ResponseEntity.ok(Map.of("message", "Ward assigned successfully"));
    }

    @DeleteMapping("/{id}/assign-ward/{wardId}")
    public ResponseEntity<?> removeWardAssignment(@PathVariable String id, @PathVariable Long wardId) {
        nurseService.removeWardAssignment(id, wardId);
        return ResponseEntity.ok(Map.of("message", "Ward assignment removed successfully"));
    }
}
