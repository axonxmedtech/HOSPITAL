package com.hms.security;

import com.hms.entity.HospitalSetting;
import com.hms.repository.HospitalSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may see a hospital's bills: the admin always; a doctor or receptionist as the hospital's
 * Billing Handler setting allows (and reception never in Solo Doctor mode). One rule shared by
 * the billing screens and the Patient File, so the two can never disagree.
 */
@Component
public class BillingAccessPolicy {

    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private HospitalSettingRepository hospitalSettingRepository;

    /** Throws {@link AccessDeniedException} with the reason when the caller may not see bills. */
    public void check() {
        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new AccessDeniedException("Invalid hospital context");
        if (role == null) throw new AccessDeniedException("Billing is not available for your role.");
        String r = role.toUpperCase(java.util.Locale.ROOT).replaceFirst("^ROLE_", "");
        if ("HOSPITAL_ADMIN".equals(r) || "ADMIN".equals(r)) return;

        HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .orElseGet(HospitalSetting::new);
        String handler = settings.getBillingHandler() != null && !settings.getBillingHandler().isBlank()
                ? settings.getBillingHandler() : "BOTH";
        boolean solo = "SOLO".equalsIgnoreCase(settings.getReceptionMode());

        if ("DOCTOR".equals(r)) {
            if (!"DOCTOR".equalsIgnoreCase(handler) && !"BOTH".equalsIgnoreCase(handler) && !solo) {
                throw new AccessDeniedException("Billing management is restricted to receptionists.");
            }
        } else if ("RECEPTIONIST".equals(r)) {
            if (!"RECEPTIONIST".equalsIgnoreCase(handler) && !"BOTH".equalsIgnoreCase(handler)) {
                throw new AccessDeniedException("Billing management is restricted to doctors.");
            }
            if (solo) throw new AccessDeniedException("Receptionist access is restricted under Solo Doctor mode.");
        } else {
            throw new AccessDeniedException("Billing is not available for your role.");
        }
    }

    public boolean allowed() {
        try {
            check();
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }
}
