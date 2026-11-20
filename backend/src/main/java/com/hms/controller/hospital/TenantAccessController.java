package com.hms.controller.hospital;

import com.hms.security.SecurityContextHelper;
import com.hms.service.subscription.PlanLimitService;
import com.hms.service.subscription.TenantAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The caller's own plan state: what the DEMO label, the renewal banners and the Plan & Usage page
 * show. Reads only the caller's own hospital.
 */
@RestController
public class TenantAccessController {

    private final SecurityContextHelper securityHelper;
    private final TenantAccessService accessService;
    private final PlanLimitService planLimits;

    public TenantAccessController(SecurityContextHelper securityHelper, TenantAccessService accessService,
                                  PlanLimitService planLimits) {
        this.securityHelper = securityHelper;
        this.accessService = accessService;
        this.planLimits = planLimits;
    }

    /** Demo / expiry state for every signed-in member of a hospital, clinic or pharmacy. */
    @GetMapping({"/hospital/access-status", "/clinic/access-status", "/pharmacy/access-status"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> accessStatus() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(accessService.describe(accessService.accessFor(hospitalId)));
    }

    /** Plan, expiry and every limit with its current use. Admin only. */
    @GetMapping({"/hospital/plan-usage", "/clinic/plan-usage", "/pharmacy/plan-usage"})
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> planUsage() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Map<String, Object> out = new LinkedHashMap<>(accessService.describe(accessService.accessFor(hospitalId)));
        out.put("limits", planLimits.usage(hospitalId));
        return ResponseEntity.ok(out);
    }
}
