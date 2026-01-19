package com.hms.service.platform;

import com.hms.dto.CreateHospitalRequest;
import com.hms.entity.AuditLog;
import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PlatformHospitalService - Service for Super Admin hospital management
 * 
 * This service handles hospital-related operations for Super Admin:
 * - Creating new hospitals with hospital admin user
 * - Listing all hospitals
 * - Activating/deactivating hospitals
 * - Managing subscription plans
 * - System audit logging
 * 
 * Only Super Admin can access these operations.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class PlatformHospitalService {

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Create a new hospital with hospital admin user
     * 
     * This method:
     * 1. Creates a new hospital record
     * 2. Creates a hospital admin user for that hospital
     * 3. Links the admin user to the hospital via hospital_id
     * 4. Logs the action
     * 
     * @param request CreateHospitalRequest containing hospital and admin details
     * @return Created Hospital entity
     * @throws RuntimeException if admin email already exists
     */
    @Transactional
    public Hospital createHospital(CreateHospitalRequest request) {
        // Check if admin email already exists
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Create hospital
        Hospital hospital = new Hospital();
        hospital.setName(request.getHospitalName());
        hospital.setIsActive(true);
        hospital.setPlan("FREE"); // Default plan
        if (request.getModules() != null && !request.getModules().isEmpty()) {
            hospital.setModules(request.getModules());
        }
        hospital = hospitalRepository.save(hospital);

        // Create hospital admin user
        User admin = new User();
        admin.setEmail(request.getAdminEmail());
        admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        admin.setName(request.getAdminName());
        admin.setRole("HOSPITAL_ADMIN");
        admin.setHospitalId(hospital.getId()); // Link to hospital
        userRepository.save(admin);

        // Log action
        logAction("HOSPITAL_CREATED", "Created hospital: " + hospital.getName() + " with admin: " + admin.getEmail());

        return hospital;
    }

    /**
     * Get all hospitals ordered by creation date with pagination
     * 
     * @param pageable Pagination information
     * @return Page of hospitals
     */
    public org.springframework.data.domain.Page<Hospital> getAllHospitals(
            org.springframework.data.domain.Pageable pageable) {
        return hospitalRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Get hospital statistics for Super Admin Overview dashboard
     * Returns counts for total, active, and inactive hospitals
     * 
     * @return Map with hospital statistics
     */
    public Map<String, Long> getHospitalStats() {
        List<Hospital> allHospitals = hospitalRepository.findAll();

        long total = allHospitals.size();
        long active = allHospitals.stream().filter(Hospital::getIsActive).count();
        long inactive = total - active;

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("inactive", inactive);

        return stats;
    }

    /**
     * Get a hospital by Public ID
     * 
     * @param publicId Hospital Public ID
     * @return Hospital entity
     * @throws RuntimeException if hospital not found
     */
    public Hospital getHospitalByPublicId(String publicId) {
        if (publicId == null) {
            throw new RuntimeException("Hospital ID cannot be null");
        }
        return hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));
    }

    /**
     * Get hospital details with admin email
     */
    public com.hms.dto.HospitalDetailsDTO getHospitalDetails(String publicId) {
        Hospital hospital = getHospitalByPublicId(publicId);

        com.hms.dto.HospitalDetailsDTO dto = new com.hms.dto.HospitalDetailsDTO();
        dto.setPublicId(hospital.getPublicId());
        dto.setCustomId(hospital.getCustomId());
        dto.setName(hospital.getName());
        dto.setIsActive(hospital.getIsActive());
        dto.setPlan(hospital.getPlan());
        dto.setModules(hospital.getModules());
        dto.setAddress(hospital.getAddress());
        dto.setPhone(hospital.getPhone());

        // Fetch Admin Email
        List<User> admins = userRepository.findByHospitalIdAndRole(hospital.getId(), "HOSPITAL_ADMIN");
        if (!admins.isEmpty()) {
            dto.setAdminEmail(admins.get(0).getEmail());
            dto.setAdminName(admins.get(0).getName());
        }

        return dto;
    }

    /**
     * Activate or deactivate a hospital
     * 
     * When a hospital is deactivated, its users cannot log in or access the system.
     * 
     * @param publicId Hospital Public ID
     * @param isActive New active status
     * @param reason   Reason for status change
     * @return Updated Hospital entity
     * @throws RuntimeException if hospital not found
     */
    public Hospital updateHospitalStatus(String publicId, Boolean isActive, String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        boolean oldStatus = hospital.getIsActive();
        hospital.setIsActive(isActive);
        Hospital savedHospital = hospitalRepository.save(hospital);

        if (oldStatus != isActive) {
            String action = isActive ? "HOSPITAL_ACTIVATED" : "HOSPITAL_BLOCKED";
            logAction(action,
                    "Updated status for hospital: " + hospital.getName() + " to " + (isActive ? "Active" : "Inactive"),
                    reason);
        }

        return savedHospital;
    }

    /**
     * Update hospital subscription plan
     * 
     * @param publicId Hospital Public ID
     * @param plan     New plan (FREE, BASIC, PREMIUM, ENTERPRISE)
     * @return Updated Hospital entity
     */
    public Hospital updateHospitalPlan(String publicId, String plan, String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        String oldPlan = hospital.getPlan();
        hospital.setPlan(plan);
        Hospital savedHospital = hospitalRepository.save(hospital);

        if (!oldPlan.equals(plan)) {
            logAction("PLAN_UPDATED",
                    "Updated plan for hospital: " + hospital.getName() + " from " + oldPlan + " to " + plan, reason);
        }

        return savedHospital;
    }

    /**
     * Update hospital enabled modules
     * 
     * @param publicId Hospital Public ID
     * @param modules  List of enabled modules
     * @return Updated Hospital entity
     */
    public Hospital updateHospitalModules(String publicId, List<String> modules, String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        if (modules == null || modules.isEmpty()) {
            throw new RuntimeException("At least one module must be enabled");
        }

        hospital.setModules(modules);
        Hospital savedHospital = hospitalRepository.save(hospital);

        logAction("MODULES_UPDATED",
                "Updated modules for hospital: " + hospital.getName() + " to " + modules, reason);

        return savedHospital;
    }

    /**
     * Reset Tenant Admin Password
     * 
     * @param publicId Hospital Public ID
     * @return Map containing "email" and "password"
     */
    public Map<String, String> resetTenantAdminPassword(String publicId, String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        List<User> admins = userRepository.findByHospitalIdAndRole(hospital.getId(), "HOSPITAL_ADMIN");
        if (admins.isEmpty()) {
            throw new RuntimeException("No admin found for this hospital");
        }

        // Reset the first admin found (usually there's only one main admin)
        User admin = admins.get(0);

        // Generate random password (8 chars)
        String newPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        admin.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(admin);

        logAction("PASSWORD_RESET", "Reset password for hospital admin: " + admin.getEmail(), reason);

        Map<String, String> result = new HashMap<>();
        result.put("email", admin.getEmail());
        result.put("password", newPassword);
        return result;
    }

    /**
     * Update hospital name and admin email
     */
    @Transactional
    public Hospital updateHospitalDetails(String publicId, String name, String adminEmail, String adminName,
            String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        boolean nameChanged = !hospital.getName().equals(name);
        String oldName = hospital.getName();
        hospital.setName(name);

        // Update Admin Email & Name
        List<User> admins = userRepository.findByHospitalIdAndRole(hospital.getId(), "HOSPITAL_ADMIN");
        boolean emailChanged = false;
        boolean adminNameChanged = false;
        String oldEmail = "";
        String oldAdminName = "";
        String adminEmailValue = adminEmail;

        if (!admins.isEmpty()) {
            User admin = admins.get(0);

            // Update Email
            if (adminEmail != null && !adminEmail.trim().isEmpty() && !admin.getEmail().equals(adminEmail)) {
                if (userRepository.existsByEmail(adminEmail)) {
                    throw new RuntimeException("Email already exists");
                }
                oldEmail = admin.getEmail();
                admin.setEmail(adminEmail);
                emailChanged = true;
            } else {
                adminEmailValue = admin.getEmail(); // Fallback if not changed
            }

            // Update Name
            if (adminName != null && !adminName.trim().isEmpty() && !admin.getName().equals(adminName)) {
                oldAdminName = admin.getName();
                admin.setName(adminName);
                adminNameChanged = true;
            }

            if (emailChanged || adminNameChanged) {
                userRepository.save(admin);
            }
        }

        Hospital savedHospital = hospitalRepository.save(hospital);

        if (nameChanged || emailChanged || adminNameChanged) {
            StringBuilder details = new StringBuilder("Updated details for hospital. ");
            if (nameChanged)
                details.append("Hospital Name: '").append(oldName).append("' -> '").append(name).append("'. ");
            if (emailChanged)
                details.append("Email: '").append(oldEmail).append("' -> '").append(adminEmailValue).append("'. ");
            if (adminNameChanged)
                details.append("Admin Name: '").append(oldAdminName).append("' -> '").append(adminName).append("'.");
            logAction("HOSPITAL_UPDATED", details.toString(), reason);
        }

        return savedHospital;
    }

    /**
     * Get all audit logs
     * 
     * @return List of audit logs
     */
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    /**
     * Helper to log actions
     */
    private void logAction(String action, String details, String reason) {
        logAction(action, details, reason, null);
    }

    private void logAction(String action, String details) {
        logAction(action, details, null, null);
    }

    private void logAction(String action, String details, String reason, Long hospitalId) {
        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setPerformedBy(currentUsername);
            log.setReason(reason);
            log.setHospitalId(hospitalId);
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Improve: Log error to console, but don't fail the operation
            System.err.println("Failed to save audit log: " + e.getMessage());
        }
    }
}
