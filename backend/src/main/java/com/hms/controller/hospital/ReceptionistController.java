package com.hms.controller.hospital;

import com.hms.entity.User;
import com.hms.service.hospital.ReceptionistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * ReceptionistController - Endpoints for managing receptionists
 * 
 * Accessible only by Hospital Admin.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/receptionists")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class ReceptionistController {

    @Autowired
    private ReceptionistService receptionistService;

    @PostMapping
    public ResponseEntity<?> createReceptionist(@RequestBody Map<String, String> payload) {
        try {
            String name = payload.get("name");
            String email = payload.get("email");
            String password = payload.get("password");

            if (name == null || email == null || password == null) {
                return ResponseEntity.badRequest().body("Name, Email, and Password are required");
            }

            User created = receptionistService.createReceptionist(name, email, password);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllReceptionists(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(receptionistService.getAllReceptionists(search, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReceptionist(@PathVariable String id,
            @RequestParam(required = false) String reason) {
        try {
            receptionistService.deleteReceptionist(id, reason);
            return ResponseEntity.ok("Receptionist deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
