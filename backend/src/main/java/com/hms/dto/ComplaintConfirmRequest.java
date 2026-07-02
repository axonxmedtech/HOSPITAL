package com.hms.dto;

import lombok.Data;

@Data
public class ComplaintConfirmRequest {
    private String role; // ENGINEER / NURSE
    private String resolution;
}
