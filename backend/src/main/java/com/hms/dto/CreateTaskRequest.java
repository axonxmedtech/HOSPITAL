package com.hms.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateTaskRequest {
    private String title;
    private String description;
    private Long assignedToNurseUserId;
    private Long ipdAdmissionId; // nullable
    private String priority = "MEDIUM"; // LOW / MEDIUM / HIGH
    private LocalDateTime dueDate;
}
