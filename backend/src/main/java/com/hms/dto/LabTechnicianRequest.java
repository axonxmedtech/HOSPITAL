package com.hms.dto;

import lombok.Data;

/**
 * LabTechnicianRequest — DTO for creating or updating a lab technician account.
 */
@Data
public class LabTechnicianRequest {
    private String name;
    private String email;
    private String password;
    private String phone;
}
