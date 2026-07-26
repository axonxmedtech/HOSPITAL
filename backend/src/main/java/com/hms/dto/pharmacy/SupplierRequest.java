package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String supplierName;
    private String contactPerson;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
    private String phone;

    @Email(message = "Invalid email format")
    private String email;

    private String address;

    @Pattern(regexp = "^([A-Za-z0-9]{15})?$", message = "GST number must be exactly 15 alphanumeric characters")
    private String gstNumber;

    @Pattern(regexp = "^([A-Za-z0-9]{10})?$", message = "PAN number must be exactly 10 alphanumeric characters")
    private String panNumber;

    private String drugLicenseNumber;

    @Min(value = 0, message = "Credit days cannot be negative")
    private Integer creditDays;

    private Boolean isActive;
}
