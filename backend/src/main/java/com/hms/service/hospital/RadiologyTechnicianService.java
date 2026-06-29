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
 * RadiologyTechnicianService — CRUD for radiology technician accounts.
 *
 * Pattern mirrors NurseService & LabTechnicianService:
 * - User table holds the auth credentials + role = "RADIOLOGY_TECHNICIAN"
 * - radiology_technicians table holds the profile data
 * - Custom IDs are sequential: RT1, RT2, ... scoped to the hospital
 *
 * Every write: hospital-scope check + AuditLog + WebSocket broadcast.
 */
@Service
public class RadiologyTechnicianService {

    private static final Logger log = LoggerFactory.getLogger(RadiologyTechnicianService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private RadiologyTechnicianRepository radiologyTechnicianRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    /**
     * Create a new radiology technician (User + RadiologyTechnician profile).
     * Assigns the next RT{n} custom ID.
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
        user.setRole("RADIOLOGY_TECHNICIAN");
        user.setHospitalId(hospitalId);
        user.setIsActive(true);
        User saved = userRepository.save(user);

        // Assign sequential custom ID: RT1, RT2, ...
        Integer maxSeq = radiologyTechnicianRepository.findMaxRadiologyTechSequence(hospitalId);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("RT" + nextSeq);
        saved = userRepository.save(saved);

        // Create profile record
        RadiologyTechnician profile = new RadiologyTechnician();
        profile.setHospitalId(hospitalId);
        profile.setName(name);
        profile.setEmail(email);
        profile.setPhone(phone != null ? phone : "");
        profile.setCustomId(saved.getCustomId());
        profile.setIsActive(true);
        radiologyTechnicianRepository.save(profile);

        audit("RADIOLOGY_TECH_CREATED", "Created radiology technician: " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** List active radiology technicians with optional name/email search. */
    public Page<User> list(String search, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (StringUtils.hasText(search)) {
            return userRepository.searchRadiologyTechnicians(hospitalId, "RADIOLOGY_TECHNICIAN", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "RADIOLOGY_TECHNICIAN", pageable);
    }

    /** Update name and phone of an existing radiology technician. */
    @Transactional
    public User update(String publicId, String name, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Radiology technician not found: " + publicId));
        if (!user.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied — wrong hospital");
        if (!"RADIOLOGY_TECHNICIAN".equals(user.getRole()))
            throw new UnauthorizedException("User is not a radiology technician");

        user.setName(name);
        User saved = userRepository.save(user);

        // Sync profile table
        radiologyTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setName(name);
            if (phone != null) p.setPhone(phone);
            radiologyTechnicianRepository.save(p);
        });

        audit("RADIOLOGY_TECH_UPDATED", "Updated radiology technician: " + user.getEmail(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    /** Soft-deactivate a radiology technician (sets isActive = false on both User and profile). */
    @Transactional
    public void deactivate(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Radiology technician not found: " + publicId));
        if (!user.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied — wrong hospital");
        if (!"RADIOLOGY_TECHNICIAN".equals(user.getRole()))
            throw new UnauthorizedException("User is not a radiology technician");

        user.setIsActive(false);
        userRepository.save(user);

        radiologyTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setIsActive(false);
            radiologyTechnicianRepository.save(p);
        });

        audit("RADIOLOGY_TECH_DEACTIVATED", "Deactivated radiology technician: " + user.getEmail(), hospitalId);
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
