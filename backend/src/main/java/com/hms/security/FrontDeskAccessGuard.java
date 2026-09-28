package com.hms.security;

import com.hms.entity.HospitalSetting;
import com.hms.repository.HospitalSettingRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Whether the caller may perform a front-desk mutation — registering or editing a patient,
 * booking an appointment.
 *
 * <p>A doctor may act as the front desk only when the hospital says so. Under HAS_RECEPTIONIST
 * these mutations stay with reception; SOLO and BOTH open them to the doctor. Admins and
 * receptionists are untouched, and so is every other role — {@code @PreAuthorize} on the handler
 * remains the role gate, this is the per-hospital condition on top of it. The dashboard hides the
 * buttons, but that is convenience: this is the boundary.
 *
 * <p>Fails closed. A hospital with no settings row yet is read through a transient
 * {@link HospitalSetting}, whose {@code receptionMode} defaults to HAS_RECEPTIONIST, so the
 * absence of configuration denies rather than grants.
 */
@Component
public class FrontDeskAccessGuard {

    private final SecurityContextHelper securityHelper;
    private final HospitalSettingRepository hospitalSettingRepository;

    public FrontDeskAccessGuard(SecurityContextHelper securityHelper,
            HospitalSettingRepository hospitalSettingRepository) {
        this.securityHelper = securityHelper;
        this.hospitalSettingRepository = hospitalSettingRepository;
    }

    /** @throws AccessDeniedException if the caller is a doctor and this hospital keeps a reception desk. */
    public void require() {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"ROLE_DOCTOR".equalsIgnoreCase(role)) {
            return;
        }
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new AccessDeniedException("Invalid hospital context");
        }
        String mode = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .orElseGet(HospitalSetting::new)
                .getReceptionMode();
        if (!"SOLO".equalsIgnoreCase(mode) && !"BOTH".equalsIgnoreCase(mode)) {
            throw new AccessDeniedException(
                    "Front-desk actions are handled by reception in this hospital.");
        }
    }
}
