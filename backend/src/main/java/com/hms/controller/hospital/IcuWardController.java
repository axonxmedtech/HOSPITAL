package com.hms.controller.hospital;

import com.hms.dto.icu.IcuWardRequest;
import com.hms.dto.icu.IcuWardResponse;
import com.hms.entity.HospitalType;
import com.hms.security.RequireModule;
import com.hms.security.TenantType;
import com.hms.service.hospital.icu.IcuWardService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for dedicated ICU wards.
 * Scoped to HOSPITAL tenant and gated by the ICU module.
 */
@RestController
@RequestMapping("/hospital/icu/wards")
@RequireModule("ICU")
@TenantType(HospitalType.HOSPITAL)
public class IcuWardController {

    @Autowired
    private IcuWardService icuWardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<List<IcuWardResponse>> list() {
        return ResponseEntity.ok(icuWardService.listIcuWards());
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<IcuWardResponse> get(@PathVariable String publicId) {
        return ResponseEntity.ok(icuWardService.getIcuWard(publicId));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<IcuWardResponse> create(@Valid @RequestBody IcuWardRequest req) {
        return ResponseEntity.ok(icuWardService.createIcuWard(req));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<IcuWardResponse> update(@PathVariable String publicId,
                                                  @Valid @RequestBody IcuWardRequest req) {
        return ResponseEntity.ok(icuWardService.updateIcuWard(publicId, req));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String publicId) {
        icuWardService.deleteIcuWard(publicId);
        return ResponseEntity.ok(Map.of("message", "ICU ward deleted successfully"));
    }

    @PostMapping("/{publicId}/incharge")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, String>> setIncharge(@PathVariable String publicId,
                                                           @RequestBody Map<String, Long> payload) {
        Long nurseProfileId = payload != null ? payload.get("nurseProfileId") : null;
        icuWardService.setIncharge(publicId, nurseProfileId);
        return ResponseEntity.ok(Map.of("message", "Incharge updated successfully"));
    }
}
