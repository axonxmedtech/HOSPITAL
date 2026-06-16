package com.hms.service.hospital;

import com.hms.entity.Receptionist;
import com.hms.entity.User;
import com.hms.repository.ReceptionistProfileRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
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
    private ReceptionistProfileRepository receptionistProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

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

        // Set sequential customId: REC1, REC2, REC3...
        Integer maxSeq = userRepository.findMaxReceptionistSequence();
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("REC" + nextSeq);
        saved = userRepository.save(saved);

        // Create receptionist profile record
        Receptionist receptionistProfile = new Receptionist();
        receptionistProfile.setHospitalId(hospitalId);
        receptionistProfile.setName(name);
        receptionistProfile.setEmail(email);
        receptionistProfile.setPhone("");
        receptionistProfile.setCustomId(saved.getCustomId());
        receptionistProfile.setIsActive(true);
        receptionistProfileRepository.save(receptionistProfile);

        logger.info("Created receptionist: {} for hospital: {}", email, hospitalId);

        logAction("RECEPTIONIST_CREATED", "Created receptionist: " + email, null, hospitalId);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after receptionist creation", e);
        }

        return saved;
    }

    /**
     * Get all active receptionists for the current hospital
     */
    public org.springframework.data.domain.Page<User> getAllReceptionists(String search,
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            logger.error("getAllReceptionists: Hospital ID not found in context");
            throw new RuntimeException("Hospital ID not found in context");
        }
        if (org.springframework.util.StringUtils.hasText(search)) {
            return userRepository.searchReceptionists(hospitalId, "RECEPTIONIST", search, pageable);
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

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after receptionist deletion", e);
        }
    }

    public void deleteReceptionist(String publicId) {
        deleteReceptionist(publicId, null);
    }

    /**
     * Get a receptionist by public ID
     */
    public User getReceptionistByPublicId(String publicId) {
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
        return user;
    }


    /**
     * Reset a receptionist's password (Hospital Admin only)
     */
    @Transactional
    public void resetReceptionistPassword(String publicId, String newPassword) {
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

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Reset password for receptionist: {}", user.getEmail());
        logAction("PASSWORD_RESET", "Reset password for receptionist: " + user.getName() + " (" + user.getEmail() + ")", null, hospitalId);
    }

    /**
     * Update a receptionist's name
     */
    @Transactional
    public User updateReceptionist(String publicId, String name) {
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

        user.setName(name);
        User saved = userRepository.save(user);

        logger.info("Updated receptionist: {}", user.getEmail());
        logAction("RECEPTIONIST_UPDATED", "Updated receptionist: " + user.getName(), null, hospitalId);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after receptionist update", e);
        }

        return saved;
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
