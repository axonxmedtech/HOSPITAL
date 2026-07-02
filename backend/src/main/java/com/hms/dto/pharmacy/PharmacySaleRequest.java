package com.hms.dto.pharmacy;

import lombok.Data;
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
    private List<SaleItemRequest> items;

    // BR-4: required when any item is a schedule-X / narcotic medicine.
    private Long witnessUserId;

    @Data
    public static class SaleItemRequest {
        private Long medicineId;
        private Long medicineBatchId;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxPercentage;
        private BigDecimal taxAmount;
        private BigDecimal discountPercentage;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
    }
}
