package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String supplierName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String gstNumber;
    private String drugLicenseNumber;
    private Integer creditDays;
    private Boolean isActive;
}
