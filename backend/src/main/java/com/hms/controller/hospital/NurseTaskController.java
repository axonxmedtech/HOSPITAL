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
                                          @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "DONE");
        if (!"DONE".equals(status) && !"SKIPPED".equals(status) && !"REFUSED".equals(status) && !"HELD".equals(status))
            return ResponseEntity.badRequest().body("status must be DONE, SKIPPED, REFUSED, or HELD");

        String notes = (String) body.get("notes");
        Double administeredQuantity = body.get("administeredQuantity") != null
                ? Double.valueOf(body.get("administeredQuantity").toString())
                : null;
        String route = (String) body.get("route");
        String injectionSite = (String) body.get("injectionSite");
        String preVitals = (String) body.get("preVitals");
        String missedReason = (String) body.get("missedReason");
        if (missedReason == null && ("SKIPPED".equals(status) || "REFUSED".equals(status) || "HELD".equals(status))) {
            missedReason = notes; // fallback if only notes are passed by UI
        }

        try {
            return ResponseEntity.ok(taskService.executeTask(
                    admissionId, taskId, status, notes, administeredQuantity, route, injectionSite, preVitals, missedReason));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException | UnauthorizedException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
