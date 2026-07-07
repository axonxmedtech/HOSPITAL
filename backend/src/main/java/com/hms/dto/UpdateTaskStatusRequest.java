package com.hms.dto;

import lombok.Data;

@Data
public class UpdateTaskStatusRequest {
    private String status;
    private String completionRemarks;
}
