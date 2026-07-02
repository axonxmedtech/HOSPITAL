package com.hms.controller.hospital;

import com.hms.dto.*;
import com.hms.service.hospital.BiomedicalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Biomedical Engineering & Medical Equipment Management (Form 36). Asset registration and
 * calibration certification are Admin-only (mirroring the Biomedical Manager/Engineer gate
 * in the blueprint); breakdown reporting and repair confirmation are open to clinical staff
 * who use the equipment day to day, until dedicated biomedical roles are wired into login.
 */
@RestController
@RequestMapping("/hospital/biomedical")
public class BiomedicalController {

    @Autowired
    private BiomedicalService biomedicalService;

    @PostMapping("/equipment")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> registerEquipment(@RequestBody EquipmentRegisterRequest request) {
        try {
            return ResponseEntity.ok(biomedicalService.registerEquipment(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/equipment")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getEquipment() {
        return ResponseEntity.ok(biomedicalService.getEquipment());
    }

    @PostMapping("/breakdown")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> openBreakdownTicket(@RequestBody BreakdownTicketRequest request) {
        try {
            return ResponseEntity.ok(biomedicalService.openBreakdownTicket(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/breakdown")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getTickets() {
        return ResponseEntity.ok(biomedicalService.getTickets());
    }

    @PostMapping("/calibration")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> recordCalibration(@RequestBody CalibrationRequest request) {
        try {
            return ResponseEntity.ok(biomedicalService.recordCalibration(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/calibration")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> getCalibrations() {
        return ResponseEntity.ok(biomedicalService.getCalibrations());
    }

    @PostMapping("/ticket/close")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<?> closeTicket(@RequestBody java.util.Map<String, Object> body) {
        try {
            Long ticketId = Long.valueOf(String.valueOf(body.get("ticketId")));
            TicketCloseRequest request = new TicketCloseRequest();
            request.setConfirmResolution(Boolean.valueOf(String.valueOf(body.get("confirmResolution"))));
            return ResponseEntity.ok(biomedicalService.closeTicket(ticketId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
