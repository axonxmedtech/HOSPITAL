package com.hms.service.hospital;

import com.hms.entity.NurseTask;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NurseTaskService {

    @Autowired private NurseTaskRepository taskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    public List<NurseTask> getTasks(Long admissionId) {
        return taskRepository.findByIpdAdmissionIdOrderByScheduledAtDesc(admissionId);
    }

    public List<NurseTask> getPendingTasks(Long admissionId) {
        return taskRepository.findByIpdAdmissionIdAndStatus(admissionId, "PENDING");
    }

    @Transactional
    public NurseTask executeTask(Long taskId, String status, String notes) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        NurseTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        if (!task.getHospitalId().equals(hospitalId))
            throw new UnauthorizedException("Access denied");
        if (!"PENDING".equals(task.getStatus()))
            throw new IllegalStateException("Task is already " + task.getStatus());

        task.setStatus(status);
        task.setExecutedAt(LocalDateTime.now());
        task.setExecutedByName(securityHelper.getCurrentUserEmail());
        task.setNotes(notes);
        return taskRepository.save(task);
    }
}
