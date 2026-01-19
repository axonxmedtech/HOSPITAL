package com.hms.service.hospital;

import com.hms.entity.User;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import com.hms.entity.AuditLog;
import com.hms.repository.AuditLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ReceptionistService - Service for managing receptionists
 * 
 * Receptionists are treated as Users with role 'RECEPTIONIST'.
 * This service handles creation, listing, and deletion of receptionists.
 * 
 * All operations are automatically filtered by hospital_id.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class ReceptionistService {

    private static final Logger logger = LoggerFactory.getLogger(ReceptionistService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Create a new receptionist
     * 
     * @param name     Name
     * @param email    Email (Login ID)
     * @param password Password
     * @return Created User entity
     */
    @Transactional
    public User createReceptionist(String name, String email, String password) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User receptionist = new User();
        receptionist.setName(name);
        receptionist.setEmail(email);
        receptionist.setPassword(passwordEncoder.encode(password));
        receptionist.setRole("RECEPTIONIST");
        receptionist.setHospitalId(hospitalId);
        receptionist.setIsActive(true);

        User saved = userRepository.save(receptionist);
        logger.info("Created receptionist: {} for hospital: {}", email, hospitalId);

        logAction("RECEPTIONIST_CREATED", "Created receptionist: " + email, null, hospitalId);

        return saved;
    }

    /**
     * Get all active receptionists for the current hospital
     */
    public org.springframework.data.domain.Page<User> getAllReceptionists(
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            logger.error("getAllReceptionists: Hospital ID not found in context");
            throw new RuntimeException("Hospital ID not found in context");
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "RECEPTIONIST", pageable);
    }

    public List<User> getAllReceptionists() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null)
            throw new RuntimeException("Hospital ID not found in context");
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "RECEPTIONIST");
    }

    /**
     * Delete (Soft Delete) a receptionist
     */
    public void deleteReceptionist(String publicId, String reason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Receptionist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Access denied: User belongs to another hospital");
        }
        if (!"RECEPTIONIST".equals(user.getRole())) {
            throw new RuntimeException("Target user is not a receptionist");
        }

        user.setIsActive(false);
        userRepository.save(user);
        logger.info("Deleted receptionist ID: {}", publicId);

        logAction("RECEPTIONIST_DELETED", "Deleted (soft) receptionist: " + user.getEmail(), reason, hospitalId);
    }

    public void deleteReceptionist(String publicId) {
        deleteReceptionist(publicId, null);
    }

    /**
     * Helper to log actions
     */
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
            System.err.println("Failed to save audit log: " + e.getMessage());
        }
    }
}
