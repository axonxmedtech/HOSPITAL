package com.hms.service.hospital;

import com.hms.entity.IpdBillingSetting;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdBillingSettingRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** The hospital's IPD billing rules. A hospital with no row gets the defaults, unsaved. */
@Service
public class IpdBillingSettingsService {

    static final int MAX_GRACE_HOURS = 12;

    private final IpdBillingSettingRepository repository;
    private final SecurityContextHelper securityHelper;
    private final com.hms.service.AuditLogService auditLogService;

    public IpdBillingSettingsService(IpdBillingSettingRepository repository, SecurityContextHelper securityHelper,
                                     com.hms.service.AuditLogService auditLogService) {
        this.repository = repository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    /** The rules of the caller's hospital. */
    @Transactional(readOnly = true)
    public IpdBillingSetting current() {
        return forHospital(requireHospital());
    }

    /** The rules of a hospital (defaults when it never saved any). Scoped by the id given. */
    @Transactional(readOnly = true)
    public IpdBillingSetting forHospital(Long hospitalId) {
        return repository.findByHospitalId(hospitalId).orElseGet(() -> {
            IpdBillingSetting d = new IpdBillingSetting();
            d.setHospitalId(hospitalId);
            return d;
        });
    }

    @Transactional
    public IpdBillingSetting update(IpdBillingSetting incoming) {
        if (!"HOSPITAL_ADMIN".equalsIgnoreCase(securityHelper.getCurrentUserRole())) {
            throw new AccessDeniedException("Only the hospital admin can change IPD billing rules");
        }
        Long hospitalId = requireHospital();
        String rule = incoming.getBedChargeRule() == null ? IpdBillingSetting.CALENDAR_DAY
                : incoming.getBedChargeRule().trim().toUpperCase(Locale.ROOT);
        if (!IpdBillingSetting.CALENDAR_DAY.equals(rule) && !IpdBillingSetting.HOURS_24.equals(rule)) {
            throw new IllegalArgumentException("Bed charge rule must be CALENDAR_DAY or HOURS_24");
        }
        int grace = incoming.getGraceHours() == null ? 0 : incoming.getGraceHours();
        if (grace < 0 || grace > MAX_GRACE_HOURS) {
            throw new IllegalArgumentException("Grace hours must be 0 to " + MAX_GRACE_HOURS);
        }
        BigDecimal minAdvance = money(incoming.getMinAdvance(), "Minimum advance");
        BigDecimal visitFee = money(incoming.getDoctorVisitFee(), "Doctor visit fee");

        IpdBillingSetting s = repository.findByHospitalId(hospitalId).orElseGet(IpdBillingSetting::new);
        s.setHospitalId(hospitalId);
        s.setBedChargeRule(rule);
        s.setGraceHours(IpdBillingSetting.HOURS_24.equals(rule) ? grace : 0);
        s.setMinAdvance(minAdvance);
        s.setDoctorVisitFee(visitFee);
        s.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        s.setUpdatedBy(securityHelper.getCurrentUserEmail());
        IpdBillingSetting saved = repository.save(s);
        try {
            auditLogService.logAction("IPD_BILLING_SETTINGS_UPDATED",
                    "IPD billing rules: beds by " + rule + (grace > 0 ? " (+" + grace + "h grace)" : "")
                            + ", minimum advance " + minAdvance + ", doctor visit fee " + visitFee,
                    securityHelper.getCurrentUserEmail(), hospitalId, "SETTINGS", String.valueOf(saved.getId()), null);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(IpdBillingSettingsService.class).warn("Failed to audit IPD billing settings", e);
        }
        return saved;
    }

    private static BigDecimal money(BigDecimal v, String label) {
        if (v == null || v.signum() == 0) return null;
        if (v.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
        if (v.compareTo(new BigDecimal("10000000")) > 0) throw new IllegalArgumentException(label + " is too large");
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }
}
