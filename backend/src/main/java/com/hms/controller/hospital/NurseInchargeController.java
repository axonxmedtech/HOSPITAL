package com.hms.controller.hospital;

import com.hms.dto.AssignPatientNurseRequest;
import com.hms.security.RequireModule;
import com.hms.service.hospital.NurseWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/hospital/nurse-incharge")
@RequireModule("NURSING")
public class NurseInchargeController {

    @Autowired private NurseWorkspaceService workspaceService;

    @GetMapping("/patients")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','NURSE_INCHARGE')")
    public ResponseEntity<?> wardPatients() {
        return ResponseEntity.ok(workspaceService.getWardPatients());
    }

    @GetMapping("/wards/{wardId}/nurses")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','NURSE_INCHARGE')")
    public ResponseEntity<?> wardStaffNurses(@PathVariable Long wardId) {
        return ResponseEntity.ok(workspaceService.getWardStaffNurses(wardId));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','NURSE_INCHARGE')")
    public ResponseEntity<?> assign(@RequestBody AssignPatientNurseRequest req) {
        workspaceService.assignPatientNurse(req.getIpdAdmissionId(), req.getNurseProfileId());
        return ResponseEntity.ok(Map.of("message", "Nurse assigned"));
    }

    @GetMapping("/wards")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','NURSE_INCHARGE')")
    public ResponseEntity<?> myWards() { return ResponseEntity.ok(workspaceService.getMyWards()); }
}
