package com.hms.controller.hospital;

import com.hms.dto.CreateIpdAdmissionRequest;
import com.hms.dto.IpdAdmissionSummaryDTO;
import com.hms.entity.IpdAdmission;
import com.hms.service.hospital.IpdAdmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ipd")
public class IpdAdmissionController {

    @Autowired
    private IpdAdmissionService ipdAdmissionService;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    @PostMapping("/admit")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> admitToIpd(@RequestBody CreateIpdAdmissionRequest req) {
        IpdAdmission ipd = ipdAdmissionService.admitFromOpd(req.getOpdId(), req.getWardId(), req.getBedId(), req.getAdmissionType(), req.getPrimaryDiagnosis());
        return ResponseEntity.ok(ipd);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','HOSPITAL_ADMIN')")
    public ResponseEntity<?> listIpdAdmissions(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ipdAdmissionService.listIpdAdmissions(page, size, search));
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

    @PostMapping("/{id}/followup")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addFollowup(@PathVariable("id") Long id, @RequestBody com.hms.dto.AddIpdFollowupRequest req) {
        com.hms.entity.MedicalRecord mr = ipdAdmissionService.addIpdFollowup(id, req.getDiagnosis(), req.getNotes(), req.getAdministeredItems());
        return ResponseEntity.ok(mr);
    }

    @PostMapping("/{id}/plan-discharge")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> planDischarge(@PathVariable("id") Long id, @RequestBody com.hms.dto.PlanDischargeRequest req) {
        com.hms.entity.DischargeSummary ds = ipdAdmissionService.planDischarge(id, req);
        return ResponseEntity.ok(ds);
    }

    @PostMapping("/{id}/confirm-discharge")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> confirmDischarge(@PathVariable("id") Long id) {
        IpdAdmission ipd = ipdAdmissionService.confirmDischarge(id);
        return ResponseEntity.ok(ipd);
    }

    @PostMapping("/{id}/administer")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> administerItems(@PathVariable("id") Long id, @RequestBody com.hms.dto.AdministerItemsRequest req) {
        ipdAdmissionService.administerItems(id, req.getAdministeredItems());
        return ResponseEntity.ok().body("{\"message\":\"Items administered successfully\"}");
    }

    @PostMapping("/{id}/administer-hospital-items")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> administerHospitalItems(@PathVariable("id") Long id, @RequestBody com.hms.dto.AdministerHospitalItemsRequest req) {
        ipdAdmissionService.administerHospitalItems(id, req.getItems());
        return ResponseEntity.ok().body("{\"message\":\"Hospital items administered successfully\"}");
    }

    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addPrescription(@PathVariable("id") Long id, @RequestBody com.hms.dto.AddIpdPrescriptionRequest req) {
        com.hms.entity.Prescription p = ipdAdmissionService.addIpdPrescription(id, req);
        return ResponseEntity.ok(p);
    }

    @PutMapping("/prescriptions/{id}/stop")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> stopPrescription(@PathVariable("id") Long id) {
        com.hms.entity.Prescription p = ipdAdmissionService.stopPrescription(id);
        return ResponseEntity.ok(p);
    }

    @PutMapping("/{id}/change-bed")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> changeBed(@PathVariable("id") Long id, @RequestParam("newBedId") Long newBedId) {
        IpdAdmission updated = ipdAdmissionService.changeBed(id, newBedId);
        return ResponseEntity.ok(updated);
    }

    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;

    @Autowired
    private com.hms.service.hospital.PatientService patientService;

    @Autowired
    private com.hms.repository.DischargeSummaryRepository dischargeSummaryRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.service.PdfService pdfService;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @GetMapping("/{id}/discharge-summary/pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'RECEPTIONIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getDischargeSummaryPdf(@PathVariable("id") Long id) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            
            IpdAdmission ipd = ipdAdmissionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("IPD admission not found"));
            if (!ipd.getHospitalId().equals(hospitalId)) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: Tenant mismatch");
            }
            
            com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId)
                    .orElseThrow(() -> new RuntimeException("Hospital not found"));
                    
            com.hms.entity.Patient patient = patientService.getPatientById(ipd.getPatientId());
            
            com.hms.entity.DischargeSummary summary = dischargeSummaryRepository.findByIpdAdmissionId(id)
                    .orElseThrow(() -> new RuntimeException("Discharge summary not found for IPD: " + id));
                    
            com.hms.entity.Doctor doctor = null;
            if (ipd.getDoctorId() != null) {
                try {
                    doctor = doctorRepository.findById(ipd.getDoctorId()).orElse(null);
                } catch (Exception ignored) {}
            }
            
            java.io.ByteArrayInputStream pdf = pdfService.generateDischargeSummaryPdf(hospital, patient, ipd, summary, doctor);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=discharge_summary_" + ipd.getIpdNumber() + ".pdf");
            
            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(new org.springframework.core.io.InputStreamResource(pdf));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
