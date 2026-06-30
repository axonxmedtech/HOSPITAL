package com.hms.controller.hospital;

import com.hms.dto.MrdArchivedDTO;
import com.hms.dto.MrdPendingDTO;
import com.hms.entity.MrdRecord;
import com.hms.service.hospital.MrdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hospital/mrd")
public class MrdController {

    @Autowired
    private MrdService mrdService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<List<MrdPendingDTO>> getPendingArchive() {
        return ResponseEntity.ok(mrdService.listPendingArchive());
    }

    @GetMapping("/archived")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<List<MrdArchivedDTO>> getArchivedRecords() {
        return ResponseEntity.ok(mrdService.listArchived());
    }

    @PostMapping("/archive")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> archiveAdmission(@RequestBody Map<String, Object> body) {
        try {
            Long ipdAdmissionId = Long.valueOf(body.get("ipdAdmissionId").toString());
            String rackLocation = body.get("rackLocation").toString();
            MrdRecord record = mrdService.archiveAdmission(ipdAdmissionId, rackLocation);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
