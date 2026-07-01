package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicineMasterRequest {
    private String medicineCode;
    
    @NotBlank(message = "Medicine name is required")
    private String medicineName;
    
    private String genericName;
    
    @NotNull(message = "Category ID is required")
    private Long categoryId;
    
    private Long manufacturerId;
    
    private String medicineType;
    
    private String scheduleType;
    
    private String dosageForm;
    
    private String strength;
    
    @NotBlank(message = "Unit of measure is required")
    @Pattern(regexp = "^(?i)(STRIP|BOTTLE|ML|TABLET|CAPSULE|TUBE|VIAL|BOX|PACK|AMP)$", message = "Invalid unit of measure")
    private String unitOfMeasure;
    
    @Min(value = 0, message = "Reorder level cannot be negative")
    private Integer reorderLevel;

    @Min(value = 0, message = "Minimum stock level cannot be negative")
    private Integer minStockLevel;

    @NotNull(message = "GST percentage is required")
    @DecimalMin(value = "0.0", message = "GST percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "GST percentage cannot exceed 100%")
    private BigDecimal gstPercentage;
    
    private Boolean requiresPrescription;
    
    private Boolean isActive;
}
