package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BloodDonorRequest {
    private String name;
    private String phone;
    private String bloodGroup;
    private String rhType;
    private LocalDate lastDonationDate;
}
