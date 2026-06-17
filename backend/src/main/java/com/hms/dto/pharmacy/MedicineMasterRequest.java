package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    
    private String unitOfMeasure;
    
    private Integer reorderLevel;

    private Integer minStockLevel;

    private BigDecimal gstPercentage;
    
    private Boolean requiresPrescription;
    
    private Boolean isActive;
}
