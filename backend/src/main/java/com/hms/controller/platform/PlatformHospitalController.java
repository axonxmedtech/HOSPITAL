package com.hms.controller.platform;

import com.hms.dto.CreateHospitalRequest;
import com.hms.entity.Hospital;
import com.hms.service.platform.PlatformHospitalService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * PlatformHospitalController - REST controller for Super Admin hospital
 * management
 * 
 * This controller provides endpoints for Super Admin to:
 * - Create new hospitals
 * - List all hospitals
 * - View hospital details
 * - Activate/deactivate hospitals
 * 
 * All endpoints require SUPER_ADMIN role.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/platform/hospitals")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformHospitalController {

    @Autowired
    private PlatformHospitalService hospitalService;

    /**
     * Create a new hospital with hospital admin user
     * 
     * @param request CreateHospitalRequest containing hospital and admin details
     * @return Created Hospital entity
     */
    @PostMapping
    public ResponseEntity<?> createHospital(@Valid @RequestBody CreateHospitalRequest request) {
        try {
            Hospital hospital = hospitalService.createHospital(request);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get all hospitals
     * 
     * @return List of all hospitals
     */
    /**
     * Get all hospitals with pagination
     * 
     * @return Page of hospitals
     */
    @GetMapping
    public ResponseEntity<Page<Hospital>> getAllHospitals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Hospital> hospitals = hospitalService.getAllHospitals(pageable);
        return ResponseEntity.ok(hospitals);
    }

    /**
     * Get hospital statistics for Super Admin Overview dashboard
     * Returns total, active, and inactive hospital counts
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getHospitalStats() {
        Map<String, Long> stats = hospitalService.getHospitalStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get hospital by ID
     * 
     * @param id Hospital ID
     * @return Hospital entity
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getHospitalById(@PathVariable String id) {
        try {
            com.hms.dto.HospitalDetailsDTO hospital = hospitalService.getHospitalDetails(id);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update hospital status (activate/deactivate)
     * 
     * @param id      Hospital ID
     * @param request Map containing "isActive" boolean value
     * @return Updated Hospital entity
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateHospitalStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        try {
            Boolean isActive = (Boolean) request.get("isActive");
            String reason = (String) request.get("reason");
            if (isActive == null) {
                return ResponseEntity.badRequest().body("isActive field is required");
            }
            Hospital hospital = hospitalService.updateHospitalStatus(id, isActive, reason);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update hospital subscription plan
     * 
     * @param id      Hospital ID
     * @param request Map containing "plan" value
     * @return Updated Hospital entity
     */
    @PutMapping("/{id}/plan")
    public ResponseEntity<?> updateHospitalPlan(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        try {
            String plan = request.get("plan");
            String reason = request.get("reason");
            if (plan == null) {
                return ResponseEntity.badRequest().body("plan field is required");
            }
            Hospital hospital = hospitalService.updateHospitalPlan(id, plan, reason);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update hospital enabled modules
     * 
     * @param id      Hospital ID
     * @param request Map containing "modules" list
     * @return Updated Hospital entity
     */
    @PutMapping("/{id}/modules")
    public ResponseEntity<?> updateHospitalModules(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> modules = (List<String>) request.get("modules");
            String reason = (String) request.get("reason");
            if (modules == null || modules.isEmpty()) {
                return ResponseEntity.badRequest().body("modules list is required");
            }
            Hospital hospital = hospitalService.updateHospitalModules(id, modules, reason);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update hospital name and admin email
     */
    @PutMapping("/{id}/details")
    public ResponseEntity<?> updateHospitalDetails(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String adminEmail = request.get("adminEmail");
            String adminName = request.get("adminName");
            String reason = request.get("reason");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Hospital name is required");
            }
            if (adminEmail == null || adminEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Admin email is required");
            }
            if (adminName == null || adminName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Admin name is required");
            }

            Hospital hospital = hospitalService.updateHospitalDetails(id, name, adminEmail, adminName, reason);
            return ResponseEntity.ok(hospital);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Reset Tenant Admin Password
     * 
     * @param id Hospital ID
     * @return Map containing "email" and "password"
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetTenantAdminPassword(@PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.get("reason") : null;
            Map<String, String> credentials = hospitalService.resetTenantAdminPassword(id, reason);
            return ResponseEntity.ok(credentials);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
