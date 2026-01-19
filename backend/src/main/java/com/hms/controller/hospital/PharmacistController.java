package com.hms.controller.hospital;

import com.hms.entity.User;
import com.hms.service.hospital.PharmacistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Map;

@RestController
@RequestMapping("/hospital/pharmacists")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class PharmacistController {

    @Autowired
    private PharmacistService pharmacistService;

    @PostMapping
    public ResponseEntity<?> createPharmacist(@RequestBody Map<String, String> payload) {
        try {
            String name = payload.get("name");
            String email = payload.get("email");
            String password = payload.get("password");

            if (name == null || email == null || password == null) {
                return ResponseEntity.badRequest().body("Name, Email, and Password are required");
            }

            User created = pharmacistService.createPharmacist(name, email, password);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllPharmacists(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(pharmacistService.getAllPharmacists(pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePharmacist(@PathVariable String id,
            @RequestParam(required = false) String reason) {
        try {
            pharmacistService.deletePharmacist(id, reason);
            return ResponseEntity.ok("Pharmacist deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
