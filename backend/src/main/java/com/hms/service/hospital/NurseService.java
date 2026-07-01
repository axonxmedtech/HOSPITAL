package com.hms.service.hospital;

import com.hms.entity.AuditLog;
import com.hms.entity.Nurse;
import com.hms.entity.NurseWardAssignment;
import com.hms.entity.User;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.NurseRepository;
import com.hms.repository.NurseWardAssignmentRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * NurseService - Service for managing nurses
 *
 * Nurses are treated as Users with role 'NURSE'.
 * This service handles creation, listing, update, deletion, password reset
 * and ward assignment for nurses.
 *
 * All operations are automatically filtered by hospital_id.
 */
@Service
public class NurseService {

    private static final Logger logger = LoggerFactory.getLogger(NurseService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NurseRepository nurseRepository;

    @Autowired
    private NurseWardAssignmentRepository nurseWardAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    /**
     * Create a new nurse
     */
    @Transactional
    public User createNurse(String name, String email, String password, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User nurse = new User();
        nurse.setName(name);
        nurse.setEmail(email);
        nurse.setPassword(passwordEncoder.encode(password));
        nurse.setRole("NURSE");
        nurse.setHospitalId(hospitalId);
        nurse.setIsActive(true);

        User saved = userRepository.save(nurse);

        // Set sequential customId: NRS1, NRS2, NRS3...
        Integer maxSeq = nurseRepository.findMaxNurseSequence(hospitalId);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("NRS" + nextSeq);
        saved = userRepository.save(saved);

        // Create nurse profile record
        Nurse nurseProfile = new Nurse();
        nurseProfile.setHospitalId(hospitalId);
        nurseProfile.setName(name);
        nurseProfile.setEmail(email);
        nurseProfile.setPhone(phone != null ? phone : "");
        nurseProfile.setCustomId(saved.getCustomId());
        nurseProfile.setUserId(saved.getId());
        nurseProfile.setIsActive(true);
        nurseRepository.save(nurseProfile);

        logger.info("Created nurse: {} for hospital: {}", email, hospitalId);

        logAction("NURSE_CREATED", "Created nurse: " + email, null, hospitalId);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after nurse creation", e);
        }

        return saved;
    }

    /**
     * Get all active nurses for the current hospital with optional search
     */
    public Page<User> getAllNurses(String search, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        if (StringUtils.hasText(search)) {
            return userRepository.searchNurses(hospitalId, "NURSE", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "NURSE", pageable);
    }

    /**
     * Update a nurse's name and phone
     */
    @Transactional
    public User updateNurse(String publicId, String name, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }

        user.setName(name);
        User saved = userRepository.save(user);

        // Also update profile if found
        nurseRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(profile -> {
            profile.setName(name);
            if (phone != null) {
                profile.setPhone(phone);
            }
            nurseRepository.save(profile);
        });

        logger.info("Updated nurse: {}", user.getEmail());
        logAction("NURSE_UPDATED", "Updated nurse: " + user.getName(), null, hospitalId);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after nurse update", e);
        }

        return saved;
    }

    /**
     * Soft delete a nurse
     */
    @Transactional
    public void deleteNurse(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }

        user.setIsActive(false);
        userRepository.save(user);

        // Also deactivate nurse profile
        nurseRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(profile -> {
            profile.setIsActive(false);
            nurseRepository.save(profile);
        });

        logger.info("Deleted (soft) nurse: {}", publicId);
        logAction("NURSE_DELETED", "Deleted (soft) nurse: " + user.getEmail(), null, hospitalId);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after nurse deletion", e);
        }
    }

    /**
     * Reset a nurse's password
     */
    @Transactional
    public void resetPassword(String publicId, String newPassword) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Reset password for nurse: {}", user.getEmail());
        logAction("PASSWORD_RESET", "Reset password for nurse: " + user.getName() + " (" + user.getEmail() + ")", null, hospitalId);
    }

    /**
     * Assign a nurse to a ward
     */
    @Transactional
    public void assignWard(String publicId, Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }

        // Find nurse profile by email
        Nurse nurseProfile = nurseRepository.findByEmailAndIsActiveTrue(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Nurse profile not found"));

        // Only add if not already assigned
        if (nurseWardAssignmentRepository.findByNurseIdAndWardId(nurseProfile.getId(), wardId).isEmpty()) {
            NurseWardAssignment assignment = new NurseWardAssignment();
            assignment.setNurseId(nurseProfile.getId());
            assignment.setWardId(wardId);
            nurseWardAssignmentRepository.save(assignment);
            logger.info("Assigned nurse {} to ward {}", publicId, wardId);
            logAction("NURSE_WARD_ASSIGNED", "Assigned nurse " + user.getEmail() + " to ward " + wardId, null, hospitalId);

            try {
                webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
            } catch (Exception e) {
                logger.warn("Failed to broadcast WebSocket refresh after ward assignment", e);
            }
        }
    }

    /**
     * Remove a ward assignment from a nurse
     */
    @Transactional
    public void removeWardAssignment(String publicId, Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }

        Nurse nurseProfile = nurseRepository.findByEmailAndIsActiveTrue(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Nurse profile not found"));

        nurseWardAssignmentRepository.findByNurseIdAndWardId(nurseProfile.getId(), wardId)
                .ifPresent(assignment -> {
                    nurseWardAssignmentRepository.delete(assignment);
                    logger.info("Removed nurse {} from ward {}", publicId, wardId);
                    logAction("NURSE_WARD_REMOVED", "Removed nurse " + user.getEmail() + " from ward " + wardId, null, hospitalId);

                    try {
                        webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
                    } catch (Exception e) {
                        logger.warn("Failed to broadcast WebSocket refresh after ward removal", e);
                    }
                });
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
