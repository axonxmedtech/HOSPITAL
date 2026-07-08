package com.hms.service.hospital;

import com.hms.entity.NurseProfile;
import com.hms.entity.User;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;

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

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * NurseService - manages NURSE staff (Phase 1 Nurse module).
 *
 * Nurses are Users with role 'NURSE' plus a {@link NurseProfile} record.
 * Cloned from {@link ReceptionistService}; nurse is HOSPITAL-tenant only and
 * every operation is scoped by hospital_id. customId is sequential: NRS1, NRS2...
 */
@Service
public class NurseService {

    private static final Logger logger = LoggerFactory.getLogger(NurseService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NurseProfileRepository nurseProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    /**
     * Create a new nurse (User with role NURSE + NurseProfile).
     */
    @Transactional
    public User createNurse(String name, String email, String password, String phone, String licenseNumber, Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        validateWard(wardId, hospitalId);

        User nurse = new User();
        nurse.setName(name);
        nurse.setEmail(email);
        nurse.setPassword(passwordEncoder.encode(password));
        nurse.setRole("NURSE");
        nurse.setHospitalId(hospitalId);
        nurse.setIsActive(true);

        User saved = userRepository.save(nurse);

        // Set sequential customId: NRS1, NRS2, NRS3...
        Integer maxSeq = userRepository.findMaxNurseSequence();
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("NRS" + nextSeq);
        saved = userRepository.save(saved);

        // Create nurse profile record
        NurseProfile profile = new NurseProfile();
        profile.setUserId(saved.getId());
        profile.setHospitalId(hospitalId);
        profile.setName(name);
        profile.setEmail(email);
        profile.setPhone(phone != null ? phone : "");
        profile.setLicenseNumber(licenseNumber);
        profile.setWardId(wardId);
        profile.setCustomId(saved.getCustomId());
        profile.setIsActive(true);
        nurseProfileRepository.save(profile);

        logger.info("Created nurse: {} for hospital: {}", email, hospitalId);
        logAction("NURSE_CREATED", "Created nurse: " + email, null, hospitalId);

        broadcastRefresh(hospitalId, "nurse creation");
        return saved;
    }

    /**
     * Get all active nurses for the current hospital (paged, optional search).
     */
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> getAllNurses(String search,
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = requireHospitalId();
        org.springframework.data.domain.Page<User> page;
        if (org.springframework.util.StringUtils.hasText(search)) {
            page = userRepository.searchNurses(hospitalId, "NURSE", search, pageable);
        } else {
            page = userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "NURSE", pageable);
        }
        return page.map(this::toNurseView);
    }

    /**
     * Flatten a nurse User + its profile (ward assignment) into the shape the
     * admin UI consumes.
     */
    private java.util.Map<String, Object> toNurseView(User u) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", u.getId());
        m.put("publicId", u.getPublicId());
        m.put("customId", u.getCustomId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("isActive", u.getIsActive());
        nurseProfileRepository.findByUserId(u.getId()).ifPresent(p -> {
            m.put("phone", p.getPhone());
            m.put("licenseNumber", p.getLicenseNumber());
            m.put("wardId", p.getWardId());
            if (p.getWardId() != null) {
                wardRepository.findById(p.getWardId())
                        .ifPresent(w -> m.put("wardName", w.getWardName()));
            }
        });
        return m;
    }

    public List<User> getAllNurses() {
        Long hospitalId = requireHospitalId();
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "NURSE");
    }

    /**
     * Soft-delete a nurse.
     */
    @Transactional
    public void deleteNurse(String publicId, String reason) {
        Long hospitalId = requireHospitalId();
        User user = requireNurse(publicId, hospitalId);

        user.setIsActive(false);
        userRepository.save(user);

        nurseProfileRepository.findByUserId(user.getId()).ifPresent(p -> {
            p.setIsActive(false);
            nurseProfileRepository.save(p);
        });

        logger.info("Deleted nurse ID: {}", publicId);
        logAction("NURSE_DELETED", "Deleted (soft) nurse: " + user.getEmail(), reason, hospitalId);
        broadcastRefresh(hospitalId, "nurse deletion");
    }

    public void deleteNurse(String publicId) {
        deleteNurse(publicId, null);
    }

    /**
     * Get a nurse by public ID.
     */
    public User getNurseByPublicId(String publicId) {
        Long hospitalId = requireHospitalId();
        return requireNurse(publicId, hospitalId);
    }

    /**
     * Update a nurse's name.
     */
    @Transactional
    public User updateNurse(String publicId, String name, Long wardId) {
        Long hospitalId = requireHospitalId();
        User user = requireNurse(publicId, hospitalId);
        validateWard(wardId, hospitalId);

        user.setName(name);
        User saved = userRepository.save(user);

        nurseProfileRepository.findByUserId(user.getId()).ifPresent(p -> {
            p.setName(name);
            if (wardId != null) p.setWardId(wardId);
            nurseProfileRepository.save(p);
        });

        logger.info("Updated nurse: {}", user.getEmail());
        logAction("NURSE_UPDATED", "Updated nurse: " + user.getName(), null, hospitalId);
        broadcastRefresh(hospitalId, "nurse update");
        return saved;
    }

    /**
     * Validate that a ward (when provided) exists and belongs to the current
     * hospital, so a nurse can only be assigned to one of this hospital's wards.
     */
    private void validateWard(Long wardId, Long hospitalId) {
        if (wardId == null) return;
        com.hms.entity.Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new IllegalArgumentException("Selected ward not found"));
        if (!hospitalId.equals(ward.getHospitalId())) {
            throw new UnauthorizedException("Ward belongs to another hospital");
        }
    }

    /**
     * Reset a nurse's password (Hospital Admin only).
     */
    @Transactional
    public void resetNursePassword(String publicId, String newPassword) {
        Long hospitalId = requireHospitalId();
        User user = requireNurse(publicId, hospitalId);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Reset password for nurse: {}", user.getEmail());
        logAction("PASSWORD_RESET", "Reset password for nurse: " + user.getName() + " (" + user.getEmail() + ")",
                null, hospitalId);
    }

    /**
     * Promote a nurse profile to Nurse Incharge (also updates the backing
     * user's role so login/authorization reflect the new privilege level).
     */
    @Transactional
    public void promote(Long nurseProfileId) {
        Long hospitalId = requireHospitalId();
        NurseProfile p = requireProfile(nurseProfileId, hospitalId);
        p.setIsIncharge(true);
        if (p.getUserId() != null) {
            userRepository.findById(p.getUserId()).ifPresent(u -> { u.setRole("NURSE_INCHARGE"); userRepository.save(u); });
        }
        nurseProfileRepository.save(p);
        auditNurse("NURSE_PROMOTED", "Promoted nurse " + p.getName() + " to incharge", hospitalId, nurseProfileId);
    }

    /**
     * Demote a Nurse Incharge back to a plain nurse. Blocked while the nurse
     * still holds any ward's incharge assignment — reassign those wards first.
     */
    @Transactional
    public void demote(Long nurseProfileId) {
        Long hospitalId = requireHospitalId();
        NurseProfile p = requireProfile(nurseProfileId, hospitalId);
        if (!wardRepository.findByHospitalIdAndInchargeNurseId(hospitalId, nurseProfileId).isEmpty()) {
            throw new IllegalArgumentException("Reassign this incharge's ward(s) before demoting");
        }
        p.setIsIncharge(false);
        if (p.getUserId() != null) {
            userRepository.findById(p.getUserId()).ifPresent(u -> { u.setRole("NURSE"); userRepository.save(u); });
        }
        nurseProfileRepository.save(p);
        auditNurse("NURSE_DEMOTED", "Demoted incharge " + p.getName() + " to nurse", hospitalId, nurseProfileId);
    }

    /**
     * Activate/deactivate a nurse profile. Deactivation is blocked while the
     * nurse still holds a ward's incharge assignment.
     */
    @Transactional
    public void setActive(Long nurseProfileId, boolean active) {
        Long hospitalId = requireHospitalId();
        NurseProfile p = requireProfile(nurseProfileId, hospitalId);
        if (!active && Boolean.TRUE.equals(p.getIsIncharge())
                && !wardRepository.findByHospitalIdAndInchargeNurseId(hospitalId, nurseProfileId).isEmpty()) {
            throw new IllegalArgumentException("Reassign this incharge's ward(s) before deactivating");
        }
        p.setIsActive(active);
        nurseProfileRepository.save(p);
        auditNurse(active ? "NURSE_ACTIVATED" : "NURSE_DEACTIVATED", p.getName(), hospitalId, nurseProfileId);
    }

    /** Resolve a NurseProfile.id from a nurse public id (for the controller layer). */
    public Long resolveProfileId(String publicId) {
        return nurseProfileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Nurse not found")).getId();
    }

    private NurseProfile requireProfile(Long id, Long hospitalId) {
        NurseProfile p = nurseProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nurse not found"));
        if (!hospitalId.equals(p.getHospitalId())) {
            throw new UnauthorizedException("Nurse belongs to another hospital");
        }
        return p;
    }

    private void auditNurse(String action, String details, Long hospitalId, Long id) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), hospitalId, "NURSE", String.valueOf(id), null);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for nurse action {}: {}", action, e.getMessage());
        }
    }

    // --- helpers ---

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return hospitalId;
    }

    private User requireNurse(String publicId, Long hospitalId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));
        if (!user.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: User belongs to another hospital");
        }
        if (!"NURSE".equals(user.getRole())) {
            throw new UnauthorizedException("Target user is not a nurse");
        }
        return user;
    }

    private void broadcastRefresh(Long hospitalId, String context) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after {}", context, e);
        }
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
            logger.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}
