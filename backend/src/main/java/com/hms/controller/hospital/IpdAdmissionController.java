package com.hms.controller.hospital;

import com.hms.audit.PatientRecordAccess;
import com.hms.security.RequireModule;
import jakarta.validation.Valid;

import com.hms.dto.CreateIpdAdmissionRequest;
import com.hms.dto.IpdAdmissionSummaryDTO;
import com.hms.entity.IpdAdmission;
import com.hms.service.hospital.IpdAdmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/hospital/ipd", "/clinic/ipd", "/pharmacy/ipd"})
public class IpdAdmissionController {

    @Autowired
    private IpdAdmissionService ipdAdmissionService;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    /** Attempts for the IPD-number clash. Two callers colliding is normal; more is a real fault. */
    private static final int ADMISSION_ATTEMPTS = 3;

    @RequireModule("IPD")
    @PostMapping("/admit")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> admitToIpd(@Valid @RequestBody CreateIpdAdmissionRequest req) {
        IpdAdmission ipd = admitWithIpdNumberRetry(req);
        return ResponseEntity.ok(ipd);
    }

    /**
     * E1 (C2) — retries an admission whose IPD number was taken by a concurrent admission.
     *
     * <p>The number is allocated as {@code MAX(sequence) + 1} with no lock, so two admissions
     * starting together compute the same one; the unique index on {@code ipd_number} then rejects
     * the loser. That is the database doing its job — but the loser is a legitimate admission and
     * used to receive an opaque 500.
     *
     * <p>The retry lives HERE, outside {@code admitFromOpd}, because that method is the
     * transaction. Catching the violation inside it would leave the caller in a rolled-back,
     * rollback-only transaction where nothing further can be written; each attempt has to be a
     * fresh transaction, which means a fresh call through the service proxy.
     */
    private IpdAdmission admitWithIpdNumberRetry(CreateIpdAdmissionRequest req) {
        for (int attempt = 1; ; attempt++) {
            try {
                return ipdAdmissionService.admitFromOpd(req.getOpdId(), req.getPatientId(), req.getDoctorId(), req.getWardId(),
                        req.getBedId(), req.getAdmissionType(), req.getPrimaryDiagnosis());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Only an IPD-number clash is safe to retry. Re-running the admission for any
                // other constraint would just repeat a failure a retry cannot fix.
                if (!isIpdNumberClash(e) || attempt >= ADMISSION_ATTEMPTS) {
                    throw new com.hms.exception.ConflictException(
                            "Could not allocate an IPD number just now. Please try again.");
                }
            }
        }
    }

    private boolean isIpdNumberClash(org.springframework.dao.DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("ipd_number")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The IPD list with history: {@code view} = CURRENT / DISCHARGED / CANCELLED / ALL, an
     * optional from–to date window and a search. Doctors see their own admissions.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','HOSPITAL_ADMIN','DOCTOR')")
    public ResponseEntity<?> listIpdAdmissions(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(required = false, defaultValue = "ALL") String view,
                                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                               @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ResponseEntity.ok(ipdAdmissionService.listIpdAdmissions(page, size, search, view, from, to));
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','DOCTOR','HOSPITAL_ADMIN')")
    @GetMapping("/admissions")
    public ResponseEntity<java.util.List<IpdAdmissionSummaryDTO>> getAdmittedIpdAdmissions() {
        java.util.List<IpdAdmissionSummaryDTO> list = ipdAdmissionService.getAdmittedIpdSummariesForCurrentUser();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> listMyIpdAdmissions() {
        return ResponseEntity.ok(ipdAdmissionService.listMyIpdAdmissionsForDoctor());
    }

    @PatientRecordAccess(record = PatientRecordAccess.Kind.ADMISSION, action = PatientRecordAccess.Action.VIEW, label = "IPD_CASE")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','DOCTOR','HOSPITAL_ADMIN')")
    public ResponseEntity<?> getIpdDetails(@PathVariable("id") Long id) {
        com.hms.dto.IpdAdmissionDetailsDTO dto = ipdAdmissionService.getIpdAdmissionDetails(id);
        // If current user is DOCTOR, hide billing section unless settings allow DOCTOR or BOTH
        String role = securityHelper.getCurrentUserRole();
        if ("DOCTOR".equalsIgnoreCase(role)) {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
            if (settings == null ||
                (!"DOCTOR".equalsIgnoreCase(settings.getBillingHandler()) &&
                 !"BOTH".equalsIgnoreCase(settings.getBillingHandler()))) {
                dto.setBilling(null);
            }
        }
        return ResponseEntity.ok(dto);
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/followup")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addFollowup(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.hms.dto.AddIpdFollowupRequest req) {
        com.hms.entity.MedicalRecord mr = ipdAdmissionService.addIpdFollowup(id, req.getDiagnosis(), req.getNotes(), req.getAdministeredItems());
        return ResponseEntity.ok(mr);
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/plan-discharge")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> planDischarge(@PathVariable("id") Long id, @RequestBody com.hms.dto.PlanDischargeRequest req) {
        com.hms.entity.DischargeSummary ds = ipdAdmissionService.planDischarge(id, req);
        return ResponseEntity.ok(ds);
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/confirm-discharge")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> confirmDischarge(@PathVariable("id") Long id,
                                              @RequestBody(required = false) java.util.Map<String, String> body) {
        java.util.Map<String, String> b = body == null ? java.util.Map.of() : body;
        IpdAdmission ipd = ipdAdmissionService.confirmDischarge(id, b.get("dischargeType"), b.get("dischargeNote"));
        return ResponseEntity.ok(ipd);
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/cancel-discharge-plan")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelDischargePlan(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(ipdAdmissionService.cancelDischargePlan(id, body == null ? null : body.get("reason")));
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/administer")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> administerItems(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.hms.dto.AdministerItemsRequest req) {
        ipdAdmissionService.administerItems(id, req.getAdministeredItems());
        return ResponseEntity.ok().body("{\"message\":\"Items administered successfully\"}");
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/administer-hospital-items")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> administerHospitalItems(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.hms.dto.AdministerHospitalItemsRequest req) {
        ipdAdmissionService.administerHospitalItems(id, req.getItems());
        return ResponseEntity.ok().body("{\"message\":\"Hospital items administered successfully\"}");
    }

    @RequireModule("IPD")
    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addPrescription(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.hms.dto.AddIpdPrescriptionRequest req) {
        com.hms.entity.Prescription p = ipdAdmissionService.addIpdPrescription(id, req);
        return ResponseEntity.ok(p);
    }

    @RequireModule("IPD")
    @PutMapping("/prescriptions/{id}/stop")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> stopPrescription(@PathVariable("id") Long id) {
        com.hms.entity.Prescription p = ipdAdmissionService.stopPrescription(id);
        return ResponseEntity.ok(p);
    }

    @RequireModule("IPD")
    @PutMapping("/{id}/change-bed")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> changeBed(@PathVariable("id") Long id, @RequestParam("newBedId") Long newBedId,
                                       @RequestParam(value = "newWardId", required = false) Long newWardId) {
        IpdAdmission updated = ipdAdmissionService.changeBed(id, newBedId, newWardId);
        return ResponseEntity.ok(updated);
    }

    /** Hands the patient over to another doctor. Body: {"doctorId": 12}. */
    @RequireModule("IPD")
    @PutMapping("/{id}/change-doctor")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> changeDoctor(@PathVariable("id") Long id,
                                          @RequestBody(required = false) java.util.Map<String, Object> body) {
        Object d = body == null ? null : body.get("doctorId");
        Long doctorId;
        try {
            doctorId = d == null || String.valueOf(d).isBlank() ? null : Long.valueOf(String.valueOf(d));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Select the doctor who takes over the patient.");
        }
        return ResponseEntity.ok(ipdAdmissionService.changeDoctor(id, doctorId));
    }
}
