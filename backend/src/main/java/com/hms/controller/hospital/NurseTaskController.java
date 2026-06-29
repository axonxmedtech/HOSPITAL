package com.hms.controller.hospital;

import com.hms.exception.UnauthorizedException;
import com.hms.service.hospital.NurseTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}/tasks")
public class NurseTaskController {

    @Autowired private NurseTaskService taskService;

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getTasks(@PathVariable Long admissionId) {
        return ResponseEntity.ok(taskService.getTasks(admissionId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPendingTasks(@PathVariable Long admissionId) {
        return ResponseEntity.ok(taskService.getPendingTasks(admissionId));
    }

    @PutMapping("/{taskId}/execute")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> executeTask(@PathVariable Long admissionId,
                                          @PathVariable Long taskId,
                                          @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "DONE");
        if (!"DONE".equals(status) && !"SKIPPED".equals(status))
            return ResponseEntity.badRequest().body("status must be DONE or SKIPPED");
        try {
            return ResponseEntity.ok(taskService.executeTask(admissionId, taskId, status, body.get("notes")));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException | UnauthorizedException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
