package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManufacturerRequest {
    @NotBlank(message = "Manufacturer name is required")
    private String manufacturerName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String licenseNumber;
    private Boolean isActive;
}
