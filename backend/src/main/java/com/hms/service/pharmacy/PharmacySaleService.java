package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.PharmacySaleRequest;
import com.hms.entity.pharmacy.*;
import com.hms.repository.pharmacy.*;
import com.hms.security.SecurityContextHelper;
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

    @Autowired
    private PharmacySaleRepository saleRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Transactional
    public PharmacySale createSale(PharmacySaleRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        PharmacySale sale = new PharmacySale();
        sale.setHospitalId(hospitalId);
        sale.setPatientId(request.getPatientId());
        sale.setSubtotal(request.getSubtotal());
        sale.setTaxAmount(request.getTaxAmount());
        sale.setDiscountAmount(request.getDiscountAmount());
        sale.setNetAmount(request.getNetAmount());
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setPaymentStatus("PAID"); // Default for now
        sale.setIsIpdBill(request.getIsIpdBill());
        sale.setIpdAdmissionId(request.getIpdAdmissionId());
        sale.setCreatedBy(userId);
        sale.setBillDate(LocalDateTime.now());

        List<PharmacySaleItem> items = new ArrayList<>();
        for (PharmacySaleRequest.SaleItemRequest itemReq : request.getItems()) {
            // Validation: Prevent negative or zero quantity theft
            if (itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Sale quantity must be positive");
            }

            // 1. Fetch and update batch with Pessimistic Lock (Prevents Race Conditions)
            // Also prevents IDOR by including hospitalId in query
            MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(itemReq.getMedicineBatchId(), hospitalId)
                    .orElseThrow(() -> new RuntimeException("Batch not found or unauthorized: " + itemReq.getMedicineBatchId()));

            if (batch.getCurrentQuantity().compareTo(itemReq.getQuantity()) < 0) {
                throw new RuntimeException("Insufficient stock for batch: " + batch.getBatchNumber());
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

            // 3. Create Sale Item
            PharmacySaleItem item = new PharmacySaleItem();
            item.setPharmacySale(sale);
            item.setMedicineId(itemReq.getMedicineId());
            item.setMedicineBatchId(itemReq.getMedicineBatchId());
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setTaxPercentage(itemReq.getTaxPercentage());
            item.setTaxAmount(itemReq.getTaxAmount());
            item.setDiscountAmount(itemReq.getDiscountAmount());
            item.setTotalAmount(itemReq.getTotalAmount());
            items.add(item);
        }

        sale.setItems(items);
        return saleRepository.save(sale);
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

        long todayCount = saleRepository.countByHospitalIdAndBillDateBetween(hospitalId, startOfDay, now);

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("todaySalesTotal", todayTotal);
        stats.put("todaySalesCount", todayCount);
        
        return stats;
    }
}
