package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.PharmacySaleRequest;
import com.hms.entity.pharmacy.*;
import com.hms.repository.pharmacy.*;
import com.hms.security.SecurityContextHelper;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PharmacySaleService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PharmacySaleService.class);

    @Autowired
    private PharmacySaleRepository saleRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.service.hospital.BillingService billingService;

    @Transactional
    public PharmacySale createSale(PharmacySaleRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        PharmacySale sale = new PharmacySale();
        sale.setHospitalId(hospitalId);
        sale.setPatientId(request.getPatientId());
        sale.setPatientName(request.getPatientName());
        sale.setSubtotal(request.getSubtotal());
        sale.setTaxAmount(request.getTaxAmount());
        sale.setTaxAmountRaw(request.getTaxAmount());
        sale.setDiscountAmount(request.getDiscountAmount());
        sale.setNetAmount(request.getNetAmount());
        sale.setNetAmountRaw(request.getNetAmount());
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setPaymentStatus("PAID"); // Default for now
        sale.setPostingStatus("POSTED");
        sale.setIsIpdBill(request.getIsIpdBill());
        sale.setIpdAdmissionId(request.getIpdAdmissionId());
        sale.setPrescriptionId(request.getPrescriptionId());
        sale.setDoctorId(request.getDoctorId());
        
        String resolvedSaleType = "WALK-IN";
        if (request.getPrescriptionId() != null) {
            resolvedSaleType = "PRESCRIPTION";
        } else if (request.getIsIpdBill() != null && request.getIsIpdBill()) {
            resolvedSaleType = "IPD";
        }
        sale.setSaleType(resolvedSaleType);
        sale.setPharmacistId(userId);

        List<PharmacySaleItem> items = new ArrayList<>();
        for (PharmacySaleRequest.SaleItemRequest itemReq : request.getItems()) {
            // Validation: Prevent negative or zero quantity theft
            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Sale quantity must be positive");
            }

            // 1. Fetch and update batch with Pessimistic Lock (Prevents Race Conditions)
            // Also prevents IDOR by including hospitalId in query
            MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(itemReq.getMedicineBatchId(), hospitalId)
                    .orElseThrow(() -> new RuntimeException("Batch not found or unauthorized: " + itemReq.getMedicineBatchId()));

            if (batch.getCurrentQuantity().compareTo(itemReq.getQuantity()) < 0) {
                throw new IllegalArgumentException("Insufficient stock for batch: " + batch.getBatchNumber());
            }

            BigDecimal qtyBefore = batch.getCurrentQuantity();
            batch.setCurrentQuantity(qtyBefore.subtract(itemReq.getQuantity()));
            batchRepository.save(batch);

            // 2. Record Inventory Transaction
            InventoryTransaction tx = new InventoryTransaction();
            tx.setHospitalId(hospitalId);
            tx.setMedicineBatchId(batch.getId());
            tx.setTransactionType("SALE");
            tx.setQuantity(itemReq.getQuantity().negate()); // Negative for sales
            tx.setQuantityBefore(qtyBefore);
            tx.setQuantityAfter(batch.getCurrentQuantity());
            tx.setReferenceType("PHARMACY_SALE");
            tx.setRemarks("Sale Bill #" + sale.getBillNumber());
            tx.setCreatedBy(userId);
            transactionRepository.save(tx);

            // 3. Populate Sale Item
            PharmacySaleItem item = new PharmacySaleItem();
            item.setPharmacySale(sale);
            item.setMedicineId(itemReq.getMedicineId());
            item.setMedicineBatchId(itemReq.getMedicineBatchId());
            item.setQuantity(itemReq.getQuantity());
            item.setQuantityRaw(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setTaxPercentage(itemReq.getTaxPercentage());
            item.setTaxPercentageRaw(itemReq.getTaxPercentage());
            item.setTaxAmount(itemReq.getTaxAmount());
            item.setDiscountPercentage(itemReq.getDiscountPercentage());
            item.setDiscountAmount(itemReq.getDiscountAmount());
            item.setTotalAmount(itemReq.getTotalAmount());
            item.setTotalAmountRaw(itemReq.getTotalAmount());
            items.add(item);
        }

        sale.setItems(items);
        PharmacySale savedSale = saleRepository.save(sale);

        // Auto-complete the prescription status to DISPENSED for all consultation records
        if (request.getPrescriptionId() != null) {
            try {
                List<com.hms.entity.Prescription> rxList = prescriptionRepository.findByMedicalRecordId(request.getPrescriptionId());
                for (com.hms.entity.Prescription rx : rxList) {
                    rx.setStatus("DISPENSED");
                    prescriptionRepository.save(rx);
                }
            } catch (Exception e) {
                // Ensure prescription update failure does not crash the sale transaction
            }
        }

        // Auto-post to IPD Bill if isIpdBill is true
        if (savedSale.getIsIpdBill() != null && savedSale.getIsIpdBill()) {
            try {
                Long ipdId = savedSale.getIpdAdmissionId();
                if (ipdId == null) {
                    List<com.hms.entity.IpdAdmission> activeAdmissions = ipdAdmissionRepository.findByHospitalIdAndStatus(hospitalId, "ADMITTED");
                    java.util.Optional<com.hms.entity.IpdAdmission> activeIpd = activeAdmissions.stream()
                        .filter(a -> a.getPatientId().equals(savedSale.getPatientId()))
                        .findFirst();
                    if (activeIpd.isPresent()) {
                        ipdId = activeIpd.get().getId();
                        savedSale.setIpdAdmissionId(ipdId);
                        saleRepository.save(savedSale);
                    }
                }
                
                if (ipdId != null) {
                    billingService.postIpdCharge(ipdId, "Pharmacy Sale Bill - #" + savedSale.getBillNumber(), savedSale.getNetAmount());
                }
            } catch (Exception e) {
                log.warn("Failed to auto-post pharmacy sale to IPD bill: {}", e.getMessage());
            }
        }

        return savedSale;
    }

    public Page<PharmacySale> getSalesHistory(Pageable pageable) {
        return saleRepository.findByHospitalIdOrderByCreatedAtDesc(securityHelper.getCurrentHospitalId(), pageable);
    }

    public PharmacySale getSaleDetails(Long id) {
        return saleRepository.findByIdAndHospitalId(id, securityHelper.getCurrentHospitalId())
                .orElseThrow(() -> new RuntimeException("Sale record not found"));
    }

    public BigDecimal getTodaySalesTotal() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        // Use a custom query for efficiency
        return saleRepository.getSumOfSalesBetween(hospitalId, startOfDay, LocalDateTime.now());
    }

    public java.util.Map<String, Object> getDashboardStats() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();

        BigDecimal todayTotal = saleRepository.getSumOfSalesBetween(hospitalId, startOfDay, now);
        if (todayTotal == null) todayTotal = BigDecimal.ZERO;

        long todayCount = saleRepository.countByHospitalIdAndCreatedAtBetween(hospitalId, startOfDay, now);

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("todaySalesTotal", todayTotal);
        stats.put("todaySalesCount", todayCount);
        
        return stats;
    }

    public PharmacySale findByBillNumber(String billNumber, Long hospitalId) {
        return saleRepository.findByBillNumberAndHospitalId(billNumber, hospitalId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + billNumber));
    }

    @Transactional
    public java.util.Map<String, Object> processPatientReturn(Long saleId, List<java.util.Map<String, Object>> returnItems) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        PharmacySale sale = saleRepository.findByIdAndHospitalId(saleId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Pharmacy sale invoice not found"));

        BigDecimal refundTotal = BigDecimal.ZERO;

        for (java.util.Map<String, Object> item : returnItems) {
            Long batchId = Long.valueOf(item.get("medicineBatchId").toString());
            BigDecimal qtyToReturn = new BigDecimal(item.get("quantityToReturn").toString());
            boolean restock = (boolean) item.get("restock");

            if (qtyToReturn.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive");
            }

            // Find matching sold item in original sale to validate
            PharmacySaleItem soldItem = sale.getItems().stream()
                    .filter(i -> i.getMedicineBatchId().equals(batchId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Medicine batch was not part of original invoice"));

            if (soldItem.getQuantity().compareTo(qtyToReturn) < 0) {
                throw new IllegalArgumentException("Cannot return more quantity than originally purchased for batch " + batchId);
            }

            // Expiry safety check: Reject returns if the medicine batch has expired
            MedicineBatch batchToCheck = batchRepository.findById(batchId)
                    .orElseThrow(() -> new RuntimeException("Batch not found or unauthorized"));
            if (batchToCheck.getExpiryDate() != null && batchToCheck.getExpiryDate().isBefore(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("Cannot return medicine '" + 
                    (batchToCheck.getMedicine() != null ? batchToCheck.getMedicine().getMedicineName() : batchToCheck.getBatchNumber()) + 
                    "' because the batch has already expired on " + batchToCheck.getExpiryDate() + "!");
            }

            // Calculate refund fraction (sellingPrice * qtyToReturn)
            BigDecimal refundValue = soldItem.getUnitPrice().multiply(qtyToReturn);
            refundTotal = refundTotal.add(refundValue);

            if (restock) {
                // Return stock back to inventory batch with Pessimistic Lock
                MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(batchId, hospitalId)
                        .orElseThrow(() -> new RuntimeException("Batch not found or unauthorized"));

                BigDecimal qtyBefore = batch.getCurrentQuantity();
                batch.setCurrentQuantity(qtyBefore.add(qtyToReturn));
                batchRepository.save(batch);

                // Record Restock Transaction
                InventoryTransaction tx = new InventoryTransaction();
                tx.setHospitalId(hospitalId);
                tx.setMedicineBatchId(batch.getId());
                tx.setTransactionType("RETURN");
                tx.setQuantity(qtyToReturn); // Positive because stock enters inventory
                tx.setQuantityBefore(qtyBefore);
                tx.setQuantityAfter(batch.getCurrentQuantity());
                tx.setReferenceType("PHARMACY_SALE");
                tx.setReferenceId(sale.getId());
                tx.setRemarks("Patient return restock for Bill #" + sale.getBillNumber());
                tx.setCreatedBy(userId);
                transactionRepository.save(tx);
            } else {
                // Record Return without restock (Disposal / Waste)
                InventoryTransaction tx = new InventoryTransaction();
                tx.setHospitalId(hospitalId);
                tx.setMedicineBatchId(batchId);
                tx.setTransactionType("RETURN");
                tx.setQuantity(BigDecimal.ZERO);
                tx.setQuantityBefore(soldItem.getQuantity());
                tx.setQuantityAfter(soldItem.getQuantity());
                tx.setReferenceType("PHARMACY_SALE");
                tx.setReferenceId(sale.getId());
                tx.setRemarks("Patient return disposal for Bill #" + sale.getBillNumber());
                tx.setCreatedBy(userId);
                transactionRepository.save(tx);
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "SUCCESS");
        result.put("refundAmount", refundTotal);
        result.put("message", "Patient return and refund processed successfully!");
        return result;
    }
}

