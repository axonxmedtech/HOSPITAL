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

import java.util.List;

import com.hms.entity.AuditLog;
import com.hms.repository.AuditLogRepository;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public User createPharmacist(String name, String email, String password) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User pharmacist = new User();
        pharmacist.setName(name);
        pharmacist.setEmail(email);
        pharmacist.setPassword(passwordEncoder.encode(password));
        pharmacist.setRole("PHARMACIST");
        pharmacist.setHospitalId(hospitalId);
        pharmacist.setIsActive(true);

        User saved = userRepository.save(pharmacist);
        logger.info("Created pharmacist: {} for hospital: {}", email, hospitalId);

        logAction("PHARMACIST_CREATED", "Created pharmacist: " + email, null, hospitalId);

        return saved;
    }

    public org.springframework.data.domain.Page<User> getAllPharmacists(String search,
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        if (org.springframework.util.StringUtils.hasText(search)) {
            return userRepository.searchPharmacists(hospitalId, "PHARMACIST", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "PHARMACIST", pageable);
    }

    public void deletePharmacist(String publicId, String reason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Pharmacist not found"));

        if (!user.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Access denied: User belongs to another hospital");
        }
        if (!"PHARMACIST".equals(user.getRole())) {
            throw new RuntimeException("Target user is not a pharmacist");
        }

        user.setIsActive(false);
        userRepository.save(user);
        logger.info("Deleted pharmacist ID: {}", publicId);

        logAction("PHARMACIST_DELETED", "Deleted (soft) pharmacist: " + user.getEmail(), reason, hospitalId);
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
