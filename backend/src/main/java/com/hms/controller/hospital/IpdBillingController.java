package com.hms.controller.hospital;

import com.hms.entity.IpdBillingSetting;
import com.hms.entity.IpdPackage;
import com.hms.security.RequireModule;
import com.hms.service.hospital.IpdBillingSettingsService;
import com.hms.service.hospital.IpdPackageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** IPD billing rules (bed-charge rule, minimum advance, visit fee) and packages. Hospital tenants only. */
@RestController
@RequireModule("IPD")
public class IpdBillingController {

    private final IpdBillingSettingsService settingsService;
    private final IpdPackageService packageService;

    public IpdBillingController(IpdBillingSettingsService settingsService, IpdPackageService packageService) {
        this.settingsService = settingsService;
        this.packageService = packageService;
    }

    @GetMapping("/hospital/settings/ipd-billing")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<IpdBillingSetting> getSettings() {
        return ResponseEntity.ok(settingsService.current());
    }

    @PutMapping("/hospital/settings/ipd-billing")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<IpdBillingSetting> updateSettings(@RequestBody IpdBillingSetting body) {
        return ResponseEntity.ok(settingsService.update(body));
    }

    /** Active packages (for admitting); {@code all=true} also lists retired ones (admin settings). */
    @GetMapping("/hospital/ipd/packages")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<List<IpdPackage>> listPackages(@RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(packageService.list(all));
    }

    @PostMapping("/hospital/ipd/packages")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<IpdPackage> createPackage(@RequestBody IpdPackage body) {
        return ResponseEntity.ok(packageService.create(body));
    }

    @PutMapping("/hospital/ipd/packages/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<IpdPackage> updatePackage(@PathVariable("id") Long id, @RequestBody IpdPackage body) {
        return ResponseEntity.ok(packageService.update(id, body));
    }
}
