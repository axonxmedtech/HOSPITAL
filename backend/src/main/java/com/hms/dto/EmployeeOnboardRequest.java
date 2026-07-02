package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeOnboardRequest {
    private Long userId;
    private String department;
    private String designation;
    private LocalDate joiningDate;
    private String licenseNumber;
    private LocalDate licenseExpiry;
}
