package com.hms.dto.pharmacy;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseRequest {
    private Long supplierId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String postingStatus; // DRAFT or POSTED
    
    @Valid
    @NotNull(message = "Purchase items list is required")
    private List<PurchaseItemRequest> items;

    @Data
    public static class PurchaseItemRequest {
        private Long medicineId;
        private String batchNumber;
        private LocalDate expiryDate;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Purchase quantity must be positive")
        private BigDecimal quantity;

        @NotNull(message = "Free quantity is required")
        @DecimalMin(value = "0.0", message = "Free quantity cannot be negative")
        private BigDecimal freeQuantity;

        private BigDecimal purchaseRate;
        private BigDecimal mrp;
        private BigDecimal sellingPrice;

        @DecimalMin(value = "0.0", message = "GST percentage cannot be negative")
        @DecimalMax(value = "100.0", message = "GST percentage cannot exceed 100%")
        private BigDecimal gstPercentage;

        private BigDecimal lineTotal;
    }
}
