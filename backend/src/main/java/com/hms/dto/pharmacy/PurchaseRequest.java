package com.hms.dto.pharmacy;

import lombok.Data;

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
    private List<PurchaseItemRequest> items;

    @Data
    public static class PurchaseItemRequest {
        private Long medicineId;
        private String batchNumber;
        private LocalDate expiryDate;
        private BigDecimal quantity;
        private BigDecimal freeQuantity;
        private BigDecimal purchaseRate;
        private BigDecimal mrp;
        private BigDecimal sellingPrice;
        private BigDecimal gstPercentage;
        private BigDecimal lineTotal;
    }
}
