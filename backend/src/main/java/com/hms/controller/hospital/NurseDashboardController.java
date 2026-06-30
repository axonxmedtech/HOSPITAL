package com.hms.controller.hospital;

import com.hms.dto.ShiftActivityDTO;
import com.hms.entity.IpdAdmission;
import com.hms.entity.NurseTask;
import com.hms.service.hospital.NurseDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/hospital/nurse/dashboard")
@PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
public class NurseDashboardController {

    @Autowired private NurseDashboardService dashboardService;

    @GetMapping("/patients")
    public ResponseEntity<List<com.hms.dto.IpdAdmissionSummaryDTO>> getMyPatients() {
        return ResponseEntity.ok(dashboardService.getMyPatients());
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<NurseTask>> getMyTasks() {
        return ResponseEntity.ok(dashboardService.getMyTasks());
    }

    @GetMapping("/shift-activity")
    public ResponseEntity<ShiftActivityDTO> getShiftActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftEnd) {
        return ResponseEntity.ok(dashboardService.getShiftActivity(shiftStart, shiftEnd));
    }
}
