package com.hms.dto;

import lombok.Data;

@Data
public class CleaningTaskRequest {
    private String location;
    private String taskType; // ROUTINE / DEEP / TERMINAL / EMERGENCY
    private String priority; // ROUTINE / URGENT
    private String assignedTo;
}
