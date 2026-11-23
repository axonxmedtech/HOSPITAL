package com.hms.service.hospital;

import com.hms.repository.HospitalSettingRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** The admission desk: reception, the admin, or a doctor where doctors run the desk (Solo / Both mode). */
@Component
public class FrontDeskAccess {

    private final SecurityContextHelper securityHelper;
    private final HospitalSettingRepository settings;

    public FrontDeskAccess(SecurityContextHelper securityHelper, HospitalSettingRepository settings) {
        this.securityHelper = securityHelper;
        this.settings = settings;
    }

    public boolean isFrontDesk(Long hospitalId) {
        String role = securityHelper.getCurrentUserRole();
        if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) return true;
        if (!"DOCTOR".equalsIgnoreCase(role)) return false;
        return settings.findByHospital_Id(hospitalId)
                .map(s -> "SOLO".equalsIgnoreCase(s.getReceptionMode()) || "BOTH".equalsIgnoreCase(s.getReceptionMode()))
                .orElse(false);
    }

    public void require(Long hospitalId, String what) {
        if (!isFrontDesk(hospitalId)) throw new AccessDeniedException("Only reception or the admin can " + what);
    }
}
