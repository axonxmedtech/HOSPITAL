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
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class IpdAdmissionController {

    @Autowired
    private IpdAdmissionService ipdAdmissionService;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @PostMapping("/admit")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> admitToIpd(@RequestBody CreateIpdAdmissionRequest req) {
        try {
            IpdAdmission ipd = ipdAdmissionService.admitFromOpd(req.getOpdId(), req.getWardId(), req.getBedId(), req.getAdmissionType(), req.getPrimaryDiagnosis());
            return ResponseEntity.ok(ipd);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','HOSPITAL_ADMIN')")
    public ResponseEntity<?> listIpdAdmissions(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String search) {
        try {
            return ResponseEntity.ok(ipdAdmissionService.listIpdAdmissions(page, size, search));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','DOCTOR')")
    @GetMapping("/admissions")
    public ResponseEntity<java.util.List<IpdAdmissionSummaryDTO>> getAdmittedIpdAdmissions() {
        java.util.List<IpdAdmissionSummaryDTO> list = ipdAdmissionService.getAdmittedIpdSummariesForCurrentUser();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> listMyIpdAdmissions() {
        try {
            return ResponseEntity.ok(ipdAdmissionService.listMyIpdAdmissionsForDoctor());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','DOCTOR','HOSPITAL_ADMIN')")
    public ResponseEntity<?> getIpdDetails(@PathVariable("id") Long id) {
        try {
            com.hms.dto.IpdAdmissionDetailsDTO dto = ipdAdmissionService.getIpdAdmissionDetails(id);
            // If current user is DOCTOR, hide billing section (sensitive)
            String role = securityHelper.getCurrentUserRole();
            if ("DOCTOR".equalsIgnoreCase(role)) {
                dto.setBilling(null);
            }
            return ResponseEntity.ok(dto);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/followup")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> addFollowup(@PathVariable("id") Long id, @RequestBody com.hms.dto.AddIpdFollowupRequest req) {
        try {
            com.hms.entity.MedicalRecord mr = ipdAdmissionService.addIpdFollowup(id, req.getDiagnosis(), req.getNotes());
            return ResponseEntity.ok(mr);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/plan-discharge")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> planDischarge(@PathVariable("id") Long id, @RequestBody com.hms.dto.PlanDischargeRequest req) {
        try {
            com.hms.entity.DischargeSummary ds = ipdAdmissionService.planDischarge(id, req);
            return ResponseEntity.ok(ds);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/confirm-discharge")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> confirmDischarge(@PathVariable("id") Long id) {
        try {
            IpdAdmission ipd = ipdAdmissionService.confirmDischarge(id);
            return ResponseEntity.ok(ipd);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> addPrescription(@PathVariable("id") Long id, @RequestBody com.hms.dto.AddIpdPrescriptionRequest req) {
        try {
            com.hms.entity.Prescription p = ipdAdmissionService.addIpdPrescription(id, req);
            return ResponseEntity.ok(p);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/prescriptions/{id}/stop")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> stopPrescription(@PathVariable("id") Long id) {
        try {
            com.hms.entity.Prescription p = ipdAdmissionService.stopPrescription(id);
            return ResponseEntity.ok(p);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PutMapping("/{id}/change-bed")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<?> changeBed(@PathVariable("id") Long id, @RequestParam("newBedId") Long newBedId) {
        try {
            IpdAdmission updated = ipdAdmissionService.changeBed(id, newBedId);
            return ResponseEntity.ok(updated);
        } catch (org.springframework.security.access.AccessDeniedException ade) {
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
