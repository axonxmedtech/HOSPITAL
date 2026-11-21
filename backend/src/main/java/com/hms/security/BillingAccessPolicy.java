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
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new AccessDeniedException("Invalid hospital context");
        String role = normalise(securityHelper.getCurrentUserRole());
        if ("HOSPITAL_ADMIN".equals(role) || "ADMIN".equals(role)) return;

        HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .orElseGet(HospitalSetting::new);
        String handler = settings.getBillingHandler() != null && !settings.getBillingHandler().isBlank()
                ? settings.getBillingHandler().toUpperCase(java.util.Locale.ROOT) : "BOTH";
        boolean solo = "SOLO".equalsIgnoreCase(settings.getReceptionMode());

        switch (role) {
            case "DOCTOR" -> checkDoctor(handler, solo);
            case "RECEPTIONIST" -> checkReceptionist(handler, solo);
            default -> throw new AccessDeniedException("Billing is not available for your role.");
        }
    }

    private static String normalise(String role) {
        return role == null ? "" : role.toUpperCase(java.util.Locale.ROOT).replaceFirst("^ROLE_", "");
    }

    /** A doctor bills when the handler is DOCTOR or BOTH, or in Solo mode (no front desk). */
    private static void checkDoctor(String handler, boolean solo) {
        if (!"DOCTOR".equals(handler) && !"BOTH".equals(handler) && !solo) {
            throw new AccessDeniedException("Billing management is restricted to receptionists.");
        }
    }

    /** Reception bills when the handler is RECEPTIONIST or BOTH, and never in Solo mode. */
    private static void checkReceptionist(String handler, boolean solo) {
        if (!"RECEPTIONIST".equals(handler) && !"BOTH".equals(handler)) {
            throw new AccessDeniedException("Billing management is restricted to doctors.");
        }
        if (solo) throw new AccessDeniedException("Receptionist access is restricted under Solo Doctor mode.");
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
