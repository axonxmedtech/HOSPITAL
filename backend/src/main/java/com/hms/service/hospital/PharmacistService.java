package com.hms.service.hospital;

import com.hms.entity.Pharmacist;
import com.hms.entity.User;
import com.hms.repository.PharmacistProfileRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.hms.entity.AuditLog;
import com.hms.repository.AuditLogRepository;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * PharmacistService - Service for managing pharmacists
 * 
 * Pharmacists are treated as Users with role 'PHARMACIST'.
 */
@Service
public class PharmacistService {

    private static final Logger logger = LoggerFactory.getLogger(PharmacistService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacistProfileRepository pharmacistProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public User createPharmacist(String name, String email, String password) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User pharmacist = new User();
        pharmacist.setName(name);
        pharmacist.setEmail(email);
        pharmacist.setPassword(passwordEncoder.encode(password));
        pharmacist.setRole("PHARMACIST");
        pharmacist.setHospitalId(hospitalId);
        pharmacist.setIsActive(true);

        User saved = userRepository.save(pharmacist);

        // Create pharmacist profile record
        Pharmacist pharmacistProfile = new Pharmacist();
        pharmacistProfile.setHospitalId(hospitalId);
        pharmacistProfile.setName(name);
        pharmacistProfile.setEmail(email);
        pharmacistProfile.setPhone("");
        pharmacistProfile.setIsActive(true);
        pharmacistProfileRepository.save(pharmacistProfile);

        logger.info("Created pharmacist: {} for hospital: {}", email, hospitalId);

        logAction("PHARMACIST_CREATED", "Created pharmacist: " + email, null, hospitalId);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after pharmacist creation", e);
        }

        return saved;
    }

    public org.springframework.data.domain.Page<User> getAllPharmacists(String search,
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        if (org.springframework.util.StringUtils.hasText(search)) {
            return userRepository.searchPharmacists(hospitalId, "PHARMACIST", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "PHARMACIST", pageable);
    }

    public void deletePharmacist(String publicId, String reason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Pharmacist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"PHARMACIST".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a pharmacist");
        }

        user.setIsActive(false);
        userRepository.save(user);
        logger.info("Deleted pharmacist ID: {}", publicId);

        logAction("PHARMACIST_DELETED", "Deleted (soft) pharmacist: " + user.getEmail(), reason, hospitalId);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after pharmacist deletion", e);
        }
    }

    /**
     * Get a pharmacist by public ID
     */
    public User getPharmacistByPublicId(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Pharmacist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"PHARMACIST".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a pharmacist");
        }
        return user;
    }


    /**
     * Reset a pharmacist's password (Hospital Admin only)
     */
    @Transactional
    public void resetPharmacistPassword(String publicId, String newPassword) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Pharmacist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"PHARMACIST".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a pharmacist");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Reset password for pharmacist: {}", user.getEmail());
        logAction("PASSWORD_RESET", "Reset password for pharmacist: " + user.getName() + " (" + user.getEmail() + ")", null, hospitalId);
    }

    /**
     * Update a pharmacist's name
     */
    @Transactional
    public User updatePharmacist(String publicId, String name) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Pharmacist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"PHARMACIST".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a pharmacist");
        }

        user.setName(name);
        User saved = userRepository.save(user);

        logger.info("Updated pharmacist: {}", user.getEmail());
        logAction("PHARMACIST_UPDATED", "Updated pharmacist: " + user.getName(), null, hospitalId);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after pharmacist update", e);
        }

        return saved;
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
            System.err.println("Failed to save audit log: " + e.getMessage());
        }
    }
}

