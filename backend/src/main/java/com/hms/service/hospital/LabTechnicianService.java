package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * LabTechnicianService — CRUD for lab technician accounts.
 *
 * Pattern mirrors NurseService:
 * - User table holds the auth credentials + role = "LAB_TECHNICIAN"
 * - lab_technicians table holds the profile data
 * - Custom IDs are sequential: LT1, LT2, ... scoped to the hospital
 *
 * Every write: hospital-scope check + AuditLog + WebSocket broadcast.
 */
@Service
public class LabTechnicianService {

    private static final Logger log = LoggerFactory.getLogger(LabTechnicianService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private LabTechnicianRepository labTechnicianRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    /**
     * Create a new lab technician (User + LabTechnician profile).
     * Assigns the next LT{n} custom ID.
     */
    @Transactional
    public User create(String name, String email, String password, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email already registered: " + email);

        // Create user account
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("LAB_TECHNICIAN");
        user.setHospitalId(hospitalId);
        user.setIsActive(true);
        User saved = userRepository.save(user);

        // Assign sequential custom ID: LT1, LT2, ...
        Integer maxSeq = labTechnicianRepository.findMaxLabTechSequence(hospitalId);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("LT" + nextSeq);
        saved = userRepository.save(saved);

        // Create profile record
        LabTechnician profile = new LabTechnician();
        profile.setHospitalId(hospitalId);
        profile.setName(name);
        profile.setEmail(email);
        profile.setPhone(phone != null ? phone : "");
        profile.setCustomId(saved.getCustomId());
        profile.setIsActive(true);
        labTechnicianRepository.save(profile);

        audit("LAB_TECH_CREATED", "Created lab technician: " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** List active lab technicians with optional name/email search. */
    public Page<User> list(String search, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (StringUtils.hasText(search)) {
            return userRepository.searchLabTechnicians(hospitalId, "LAB_TECHNICIAN", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "LAB_TECHNICIAN", pageable);
    }

    /** Update name and phone of an existing lab technician. */
    @Transactional
    public User update(String publicId, String name, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Lab technician not found: " + publicId));
        if (!user.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied — wrong hospital");
        if (!"LAB_TECHNICIAN".equals(user.getRole()))
            throw new UnauthorizedException("User is not a lab technician");

        user.setName(name);
        User saved = userRepository.save(user);

        // Sync profile table
        labTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setName(name);
            if (phone != null) p.setPhone(phone);
            labTechnicianRepository.save(p);
        });

        audit("LAB_TECH_UPDATED", "Updated lab technician: " + user.getEmail(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Soft-deactivate a lab technician (sets isActive = false on both User and profile). */
    @Transactional
    public void deactivate(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Lab technician not found: " + publicId));
        if (!user.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied — wrong hospital");
        if (!"LAB_TECHNICIAN".equals(user.getRole()))
            throw new UnauthorizedException("User is not a lab technician");

        user.setIsActive(false);
        userRepository.save(user);

        labTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setIsActive(false);
            labTechnicianRepository.save(p);
        });

        audit("LAB_TECH_DEACTIVATED", "Deactivated lab technician: " + user.getEmail(), hospitalId);
        broadcast(hospitalId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void audit(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(actor);
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private void broadcast(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
