package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.TrainingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * HR Training / Learning Management (Form 04). Course/session/attendance management and
 * verification are Admin-only (mirroring the HR Executive/Trainer/Department Head gates in
 * the blueprint, until those roles are wired into login); reads are open to clinical staff
 * for self-service history lookups.
 */
@RestController
@RequestMapping("/hospital/training")
public class TrainingController {

    @Autowired
    private TrainingService trainingService;

    @PostMapping("/masters")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> createMaster(@RequestBody TrainingMasterRequest request) {
        try {
            return ResponseEntity.ok(trainingService.createMaster(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/masters")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST')")
    public ResponseEntity<?> getMasters() {
        return ResponseEntity.ok(trainingService.getMasters());
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> createSession(@RequestBody TrainingSessionRequest request) {
        try {
            return ResponseEntity.ok(trainingService.createSession(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST')")
    public ResponseEntity<?> getSessions() {
        return ResponseEntity.ok(trainingService.getSessions());
    }

    @PostMapping("/sessions/{id}/cancel")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelSession(@PathVariable Long id, @RequestBody SessionCancelRequest request) {
        try {
            return ResponseEntity.ok(trainingService.cancelSession(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sessions/{id}/verify")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifySession(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(trainingService.verifySession(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/attendance")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> markAttendance(@RequestBody TrainingAttendanceMarkRequest request) {
        try {
            return ResponseEntity.ok(trainingService.markAttendance(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getAttendance() {
        return ResponseEntity.ok(trainingService.getAttendance());
    }

    @PutMapping("/attendance/{id}/correct")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> correctAttendance(@PathVariable Long id, @RequestBody TrainingAttendanceCorrectRequest request) {
        try {
            return ResponseEntity.ok(trainingService.correctAttendance(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/employees/{id}/history")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST')")
    public ResponseEntity<?> getEmployeeHistory(@PathVariable Long id) {
        return ResponseEntity.ok(trainingService.getEmployeeHistory(id));
    }

    @GetMapping("/certifications")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE', 'PHARMACIST', 'RECEPTIONIST')")
    public ResponseEntity<?> getCertifications() {
        return ResponseEntity.ok(trainingService.getCertifications());
    }
}
