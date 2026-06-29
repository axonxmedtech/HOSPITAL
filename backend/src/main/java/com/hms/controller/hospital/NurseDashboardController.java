package com.hms.controller.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.NurseTask;
import com.hms.service.hospital.NurseDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/hospital/nurse/dashboard")
@PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
public class NurseDashboardController {

    @Autowired private NurseDashboardService dashboardService;

    @GetMapping("/patients")
    public ResponseEntity<List<IpdAdmission>> getMyPatients() {
        return ResponseEntity.ok(dashboardService.getMyPatients());
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<NurseTask>> getMyTasks() {
        return ResponseEntity.ok(dashboardService.getMyTasks());
    }
}
