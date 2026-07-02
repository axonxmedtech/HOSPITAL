package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EquipmentRegisterRequest {
    private String equipmentName;
    private String category;
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String department;
    private String location;
    private LocalDate warrantyExpiry;
}
