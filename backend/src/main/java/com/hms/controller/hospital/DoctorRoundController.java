package com.hms.controller.hospital;

import com.hms.dto.DoctorRoundRequest;
import com.hms.service.hospital.DoctorRoundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ipd/{admissionId}/rounds")
public class DoctorRoundController {

    @Autowired
    private DoctorRoundService roundService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getRoundsHistory(@PathVariable Long admissionId) {
        try {
            return ResponseEntity.ok(roundService.getRoundsHistory(admissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> logRound(@PathVariable Long admissionId,
                                      @RequestBody DoctorRoundRequest request) {
        try {
            return ResponseEntity.ok(roundService.logRound(admissionId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Amends a signed note (Forms 11/13): original stays immutable, a linked correction is created. */
    @PostMapping("/{roundId}/amend")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> amendRound(@PathVariable Long admissionId,
                                        @PathVariable Long roundId,
                                        @RequestBody DoctorRoundRequest request) {
        try {
            return ResponseEntity.ok(roundService.amendRound(roundId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
