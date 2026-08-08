package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.IcuStayResponse;
import com.hms.service.hospital.IcuStayService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ICU stays for an admission.
 *
 * <p>Read-only. Moving a patient into or out of ICU goes through the existing IPD bed-change
 * endpoint, which already releases the old bed and writes the bed history — a second transfer path
 * would be a parallel implementation of the thing that keeps the stay on one bill.
 *
 * <p>Hospital-only, not aliased to {@code /clinic/**}: a clinic has no inpatients, so it has no
 * intensive care.
 */
@RestController
@RequestMapping("/hospital/icu")
public class IcuController {

    private final IcuStayService icuStayService;

    public IcuController(IcuStayService icuStayService) {
        this.icuStayService = icuStayService;
    }

    @GetMapping("/stays/{admissionId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST', 'NURSE', 'NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<List<IcuStayResponse>>> stays(@PathVariable Long admissionId) {
        return ResponseEntity.ok(ApiResponse.ok(icuStayService.forAdmission(admissionId)));
    }
}
