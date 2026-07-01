package com.hms.dto.pharmacy;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PharmacySaleRequest {
    private Long patientId;
    private String patientName;
    private String paymentMethod;
    private Boolean isIpdBill;
    private Long ipdAdmissionId;
    private Long prescriptionId;
    private Long doctorId;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;

    @Valid
    @NotNull(message = "Sale items list is required")
    private List<SaleItemRequest> items;

    @Data
    public static class SaleItemRequest {
        private Long medicineId;
        private Long medicineBatchId;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Sale quantity must be positive")
        private BigDecimal quantity;

        private BigDecimal unitPrice;

        @DecimalMin(value = "0.0", message = "Tax percentage cannot be negative")
        @DecimalMax(value = "100.0", message = "Tax percentage cannot exceed 100%")
        private BigDecimal taxPercentage;

        private BigDecimal taxAmount;
        private BigDecimal discountPercentage;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
    }
}
