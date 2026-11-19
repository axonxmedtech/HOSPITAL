package com.hms.controller.hospital;

import com.hms.entity.Bed;
import com.hms.entity.IcuCleaningTask;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.IcuCleaningTaskRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/hospital/icu/cleaning", "/clinic/icu/cleaning", "/pharmacy/icu/cleaning"})
public class IcuCleaningController {

    @Autowired
    private IcuCleaningTaskRepository cleaningTaskRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    // Bed status changes go through the audited service, which also keeps tasks in step.
    @Autowired
    private com.hms.service.hospital.BedStatusService bedStatusService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE_INCHARGE', 'NURSE', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<List<IcuCleaningTask>> getTasks(@RequestParam(required = false) String status) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(cleaningTaskRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status));
        }
        return ResponseEntity.ok(cleaningTaskRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE_INCHARGE', 'NURSE', 'RECEPTIONIST')")
    public ResponseEntity<?> createTask(@RequestBody IcuCleaningTask task) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (task.getTaskDescription() == null || task.getTaskDescription().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task description is required"));
        }
        task.setHospitalId(hospitalId);
        String role = securityHelper.getCurrentUserRole() != null ? securityHelper.getCurrentUserRole() : "STAFF";
        String email = securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "user";
        task.setCreatedBy(role + " (" + email + ")");
        if (task.getStatus() == null) {
            task.setStatus("PENDING");
        }
        if (task.getPriority() == null) {
            task.setPriority("MEDIUM");
        }

        // Link the bed; save the task first so the status change below finds it open and does
        // not queue a duplicate.
        java.util.Optional<com.hms.entity.Bed> bed = findBedForTask(hospitalId, task);
        bed.ifPresent(b -> {
            task.setBedId(b.getBedId());
            task.setWardId(b.getWardId());
        });
        IcuCleaningTask saved = cleaningTaskRepository.save(task);
        bed.ifPresent(b -> {
            if (com.hms.entity.BedStatus.AVAILABLE.equalsIgnoreCase(b.getStatus())) {
                bedStatusService.change(b.getBedId(), com.hms.entity.BedStatus.CLEANING, "Cleaning task created");
            }
        });
        return ResponseEntity.ok(saved);
    }

    // DOCTOR too: in Solo mode the doctor is the only one at the front desk.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE_INCHARGE', 'NURSE', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        IcuCleaningTask task = cleaningTaskRepository.findById(id)
                .filter(t -> t.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new ResourceNotFoundException("Cleaning task not found"));

        String status = body.get("status");
        if (status != null && !status.isEmpty()) {
            task.setStatus(status.toUpperCase());
            cleaningTaskRepository.save(task);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                // Only a bed awaiting cleaning becomes available; an occupied bed or one under
                // maintenance is left as it is.
                findBedForTask(hospitalId, task).ifPresent(b -> {
                    if (com.hms.entity.BedStatus.CLEANING.equalsIgnoreCase(b.getStatus())) {
                        bedStatusService.change(b.getBedId(), com.hms.entity.BedStatus.AVAILABLE, "Cleaning task completed");
                    }
                });
            }
        }
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE_INCHARGE', 'NURSE', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        IcuCleaningTask task = cleaningTaskRepository.findById(id)
                .filter(t -> t.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new ResourceNotFoundException("Cleaning task not found"));

        findBedForTask(hospitalId, task).ifPresent(b -> {
            if ("cleaning".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("available");
                bedRepository.save(b);
            }
        });

        cleaningTaskRepository.delete(task);
        return ResponseEntity.ok(Map.of("message", "Cleaning task deleted successfully"));
    }

    private Optional<Bed> findBedForTask(Long hospitalId, IcuCleaningTask task) {
        if (task.getBedId() != null) {
            return bedRepository.findById(task.getBedId()).filter(b -> hospitalId.equals(b.getHospitalId()));
        }
        String bedNum = task.getBedNumber() != null ? task.getBedNumber().trim().toLowerCase() : "";
        if (bedNum.isEmpty() || "-".equals(bedNum)) return Optional.empty();

        String wardName = task.getWardName() != null ? task.getWardName().trim().toLowerCase() : "";

        List<Bed> beds = bedRepository.findByHospitalId(hospitalId);
        return beds.stream().filter(b -> {
            String bCode = b.getBedCode() != null ? b.getBedCode().trim().toLowerCase() : "";
            boolean bedMatches = bCode.equals(bedNum) || bCode.endsWith("-" + bedNum) || bCode.endsWith(" " + bedNum) || bedNum.endsWith(bCode);
            if (!bedMatches) {
                String cleanB = bCode.replaceAll("^.*(?:-|\\bbed\\b|\\bward\\b\\s*\\d+\\s*)", "").trim();
                String cleanT = bedNum.replaceAll("^.*(?:-|\\bbed\\b|\\bward\\b\\s*\\d+\\s*)", "").trim();
                bedMatches = !cleanT.isEmpty() && cleanT.equals(cleanB);
            }
            if (!bedMatches) return false;

            if (!wardName.isEmpty() && !"icu".equals(wardName)) {
                Ward w = wardRepository.findById(b.getWardId()).orElse(null);
                if (w != null) {
                    String wName = w.getWardName() != null ? w.getWardName().trim().toLowerCase() : "";
                    return wName.equals(wardName) || wName.contains(wardName) || wardName.contains(wName);
                }
            }
            return true;
        }).findFirst();
    }
}
