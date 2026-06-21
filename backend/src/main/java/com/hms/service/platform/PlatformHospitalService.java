package com.hms.service.platform;

import com.hms.dto.CreateHospitalRequest;
import com.hms.entity.AuditLog;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalAdmin;
import com.hms.entity.HospitalType;
import com.hms.entity.User;
import com.hms.entity.Doctor;
import com.hms.entity.HospitalSetting;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.HospitalAdminRepository;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalSettingRepository;
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

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private HospitalAdminRepository hospitalAdminRepository;

    @Autowired
    private HospitalSettingRepository hospitalSettingRepository;

    @Autowired
    private PlatformPlanService planService;

    @Autowired
    private HospitalPlanSubscriptionRepository subscriptionRepository;

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
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getAdminEmail());
        }

        HospitalType type = HospitalType.valueOf(request.getType());

        Hospital hospital = new Hospital();
        hospital.setName(request.getHospitalName());
        hospital.setIsActive(true);
        hospital.setType(type);
        if (type != HospitalType.PHARMACY && request.getIsSingleDoctor() != null) {
            hospital.setIsSingleDoctor(request.getIsSingleDoctor());
        } else {
            hospital.setIsSingleDoctor(false);
        }
        hospital = hospitalRepository.save(hospital);

        HospitalSetting settings = new HospitalSetting();
        settings.setHospital(hospital);
        settings.setReceptionMode("HAS_RECEPTIONIST");
        settings.setBillingHandler("RECEPTIONIST");
        settings.setInClinic(false);
        hospitalSettingRepository.save(settings);

        User admin = new User();
        admin.setEmail(request.getAdminEmail());
        admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        admin.setName(request.getAdminName());
        admin.setRole("HOSPITAL_ADMIN");
        admin.setHospitalId(hospital.getId());
        userRepository.save(admin);

        HospitalAdmin hospitalAdmin = new HospitalAdmin();
        hospitalAdmin.setHospitalId(hospital.getId());
        hospitalAdmin.setName(request.getAdminName());
        hospitalAdmin.setEmail(request.getAdminEmail());
        hospitalAdmin.setPhone("");
        hospitalAdmin.setIsActive(true);
        hospitalAdminRepository.save(hospitalAdmin);

        if (type != HospitalType.PHARMACY && Boolean.TRUE.equals(hospital.getIsSingleDoctor())) {
            Doctor doctor = new Doctor();
            doctor.setHospitalId(hospital.getId());
            doctor.setEmail(admin.getEmail());
            doctor.setName(admin.getName());
            doctor.setSpecialization("General Physician");
            doctor.setPhone("0000000055");
            doctor.setIsActive(true);
            doctorRepository.save(doctor);
        }

        if (request.getPlanPublicId() != null && !request.getPlanPublicId().isBlank()) {
            String billingPeriod = (request.getBillingPeriod() != null && !request.getBillingPeriod().isBlank())
                    ? request.getBillingPeriod() : "MONTHLY";
            com.hms.dto.AssignPlanRequest assignReq = new com.hms.dto.AssignPlanRequest();
            assignReq.setHospitalPublicId(hospital.getPublicId());
            assignReq.setBillingPeriod(billingPeriod);
            planService.assignPlan(request.getPlanPublicId(), assignReq);
        }

        logAction("HOSPITAL_CREATED",
            "Created " + type + ": " + hospital.getName() + " with admin: " + admin.getEmail());

        return hospitalRepository.findById(hospital.getId()).orElse(hospital);
    }

    /**
     * Get all hospitals ordered by creation date with pagination
     * 
     * @param pageable Pagination information
     * @return Page of hospitals
     */
    public org.springframework.data.domain.Page<Hospital> getAllHospitals(
            org.springframework.data.domain.Pageable pageable,
            String type) {
        if (type != null && !type.isBlank()) {
            return hospitalRepository.findByTypeOrderByCreatedAtDesc(HospitalType.valueOf(type), pageable);
        }
        return hospitalRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Get hospital statistics for Super Admin Overview dashboard
     * Returns counts for total, active, and inactive hospitals
     * 
     * @return Map with hospital statistics
     */
    public Map<String, Long> getHospitalStats() {
        long total = hospitalRepository.count();
        long active = hospitalRepository.countByIsActive(true);
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
            throw new IllegalArgumentException("Hospital ID cannot be null");
        }
        return hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found: " + publicId));
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
        // dto.setPlan(hospital.getPlan()); // Removed - plan field replaced with subscription info (Task 12)
        dto.setModules(hospital.getModules());
        dto.setAddress(hospital.getAddress());
        dto.setPhone(hospital.getPhone());
        dto.setIsSingleDoctor(hospital.getIsSingleDoctor());
        dto.setType(hospital.getType() != null ? hospital.getType().name() : "HOSPITAL");
        dto.setSubscriptionStatus(hospital.getSubscriptionStatus());

        subscriptionRepository.findByHospitalIdAndIsCurrentTrue(hospital.getId()).ifPresent(sub -> {
            dto.setPlanName(sub.getPlan().getName());
            dto.setBillingPeriod(sub.getBillingPeriod().name());
            dto.setAssignedAt(sub.getAssignedAt());
            dto.setExpiresAt(sub.getExpiresAt());
        });

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
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found: " + publicId));

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
     * Reset Tenant Admin Password
     * Password is now provided by Super Admin — not auto-generated.
     *
     * @param publicId    Hospital Public ID
     * @param newPassword New password chosen by Super Admin
     * @param reason      Reason for reset
     * @return Map containing "email" and success confirmation
     */
    public Map<String, String> resetTenantAdminPassword(String publicId, String newPassword, String reason) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found: " + publicId));

        List<User> admins = userRepository.findByHospitalIdAndRole(hospital.getId(), "HOSPITAL_ADMIN");
        if (admins.isEmpty()) {
            throw new ResourceNotFoundException("No admin found for this hospital");
        }

        User admin = admins.get(0);
        admin.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(admin);

        logAction("PASSWORD_RESET", "Reset password for hospital admin: " + admin.getEmail(), reason);

        Map<String, String> result = new HashMap<>();
        result.put("email", admin.getEmail());
        result.put("message", "Password reset successfully");
        return result;
    }

    /**
     * Update hospital name, admin email, and single doctor status
     */
    @Transactional
    public Hospital updateHospitalDetails(String publicId, String name, String adminEmail, String adminName,
            String reason, Boolean isSingleDoctor) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found: " + publicId));

        boolean nameChanged = !hospital.getName().equals(name);
        String oldName = hospital.getName();
        hospital.setName(name);

        boolean isSingleDoctorChanged = isSingleDoctor != null && !isSingleDoctor.equals(hospital.getIsSingleDoctor());
        if (isSingleDoctor != null) {
            hospital.setIsSingleDoctor(isSingleDoctor);
        }

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
                    throw new IllegalArgumentException("Email already exists: " + adminEmail);
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

        // If Single Doctor Clinic is enabled, ensure a Doctor profile exists (only if OPD is enabled)
        if (Boolean.TRUE.equals(savedHospital.getIsSingleDoctor()) && savedHospital.getModules() != null && savedHospital.getModules().contains("OPD")) {
            String targetEmail = adminEmail != null ? adminEmail.trim() : (admins.isEmpty() ? "" : admins.get(0).getEmail());
            String targetName = adminName != null ? adminName.trim() : (admins.isEmpty() ? "" : admins.get(0).getName());
            
            if (!targetEmail.isEmpty()) {
                boolean doctorExists = doctorRepository.findByEmailAndHospitalId(targetEmail, savedHospital.getId()).isPresent();
                if (!doctorExists) {
                    Doctor doctor = new Doctor();
                    doctor.setHospitalId(savedHospital.getId());
                    doctor.setEmail(targetEmail);
                    doctor.setName(targetName);
                    doctor.setSpecialization("General Physician");
                    doctor.setPhone("0000000055");
                    doctor.setIsActive(true);
                    doctorRepository.save(doctor);
                }
            }
        }

        if (nameChanged || emailChanged || adminNameChanged || isSingleDoctorChanged) {
            StringBuilder details = new StringBuilder("Updated details for hospital. ");
            if (nameChanged)
                details.append("Hospital Name: '").append(oldName).append("' -> '").append(name).append("'. ");
            if (emailChanged)
                details.append("Email: '").append(oldEmail).append("' -> '").append(adminEmailValue).append("'. ");
            if (adminNameChanged)
                details.append("Admin Name: '").append(oldAdminName).append("' -> '").append(adminName).append("'. ");
            if (isSingleDoctorChanged)
                details.append("Single Doctor Mode: '").append(!isSingleDoctor).append("' -> '").append(isSingleDoctor).append("'.");
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
