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

    @Autowired
    private PharmacySaleRepository saleRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.pharmacy.PharmacySaleItemRepository saleItemRepository;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;

    @Autowired private com.hms.service.RealtimeNotifier notifier;

    /** What a counter may record as the way the customer paid. */
    static final java.util.Set<String> PAYMENT_METHODS = java.util.Set.of("CASH", "UPI", "CARD", "IPD_BILL");

    @Autowired
    private PharmacyInvoiceNumberService invoiceNumberService;

    @Autowired
    private com.hms.repository.pharmacy.PharmacySaleReturnRepository saleReturnRepository;

    @Autowired
    private PharmacyAuditTrail auditTrail;

    /**
     * Records a counter sale. The browser says WHAT is sold (batch, quantity) and the bill
     * discount; every amount is worked out here from the batch's selling price, which includes
     * GST and never exceeds the MRP. Amounts sent by the browser are ignored, so a bill can never
     * be saved at a price the shelf does not carry, and the customer never pays above MRP.
     */
    @Transactional
    public PharmacySale createSale(PharmacySaleRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();
        Long userId = securityHelper.getCurrentUserId();

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the bill");
        }
        String paymentMethod = request.getPaymentMethod() == null ? "CASH"
                : request.getPaymentMethod().trim().toUpperCase(java.util.Locale.ROOT);
        if (!PAYMENT_METHODS.contains(paymentMethod)) {
            throw new IllegalArgumentException("Payment method must be Cash, UPI or Card");
        }

        // 1. Lock and check every batch first, so the bill is priced from what is really on the shelf.
        List<MedicineBatch> batches = new ArrayList<>();
        List<PharmacyBillCalculator.Line> lines = new ArrayList<>();
        for (PharmacySaleRequest.SaleItemRequest itemReq : request.getItems()) {
            MedicineBatch batch = lockSellableBatch(itemReq, hospitalId, branchId);
            batches.add(batch);
            lines.add(new PharmacyBillCalculator.Line(itemReq.getQuantity(), batch.getSellingPrice(), gstRateOf(batch)));
        }
        PharmacyBillCalculator.Bill bill = PharmacyBillCalculator.calculate(lines, request.getDiscountAmount());

        PharmacySale sale = new PharmacySale();
        sale.setHospitalId(hospitalId);
        sale.setBranchId(branchId);
        sale.setBillNumber(invoiceNumberService.next(hospitalId, branchId));
        sale.setPatientId(request.getPatientId());
        sale.setPatientName(request.getPatientName());
        // subtotal = shelf value (GST included, before discount); tax = GST contained in the net.
        sale.setSubtotal(bill.gross());
        sale.setDiscountAmount(bill.discount());
        sale.setTaxAmount(bill.gst());
        sale.setTaxAmountRaw(bill.gst());
        sale.setNetAmount(bill.net());
        sale.setNetAmountRaw(bill.net());
        sale.setPaymentMethod(paymentMethod);
        sale.setPaymentStatus("PAID");
        sale.setPostingStatus("POSTED");
        sale.setIsIpdBill("IPD_BILL".equals(paymentMethod));
        sale.setIpdAdmissionId(request.getIpdAdmissionId());
        sale.setPrescriptionId(request.getPrescriptionId());
        sale.setDoctorId(request.getDoctorId());
        String saleType = "WALK-IN";
        if (request.getPrescriptionId() != null) {
            saleType = "PRESCRIPTION";
        } else if (Boolean.TRUE.equals(sale.getIsIpdBill())) {
            saleType = "IPD";
        }
        sale.setSaleType(saleType);
        sale.setPharmacistId(userId);

        // 2. Deduct stock and build the lines. The stock movements are saved after the sale, once
        // it has an id and a bill number to point back to.
        List<InventoryTransaction> stockMovements = new ArrayList<>();
        List<PharmacySaleItem> items = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            items.add(sellLine(request.getItems().get(i).getQuantity(), batches.get(i), bill.lines().get(i),
                    lines.get(i), sale, hospitalId, branchId, userId, stockMovements));
        }
        sale.setItems(items);
        PharmacySale savedSale = saleRepository.save(sale);

        for (InventoryTransaction tx : stockMovements) {
            tx.setReferenceId(savedSale.getId());
            tx.setRemarks("Sale Bill #" + savedSale.getBillNumber());
        }
        transactionRepository.saveAll(stockMovements);

        if (request.getPrescriptionId() != null) {
            markPrescriptionsDispensed(request.getPrescriptionId(), hospitalId);
        }

        // A sale deducts stock. A second pharmacist at the same counter must see the new quantity
        // immediately, or they will sell against a batch that is already empty.
        notifier.refresh(hospitalId);
        return savedSale;
    }

    /**
     * The batch, locked, after checking it may be sold: it belongs to this pharmacy (and branch),
     * it is not blocked or disposed, it has not expired, and it holds enough stock.
     */
    private MedicineBatch lockSellableBatch(PharmacySaleRequest.SaleItemRequest itemReq, Long hospitalId, Long branchId) {
        if (itemReq.getQuantity() == null || itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sale quantity must be positive");
        }
        if (itemReq.getMedicineBatchId() == null) {
            throw new IllegalArgumentException("Choose the batch to sell from");
        }
        MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(itemReq.getMedicineBatchId(), hospitalId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized: " + itemReq.getMedicineBatchId()));
        String label = batchLabel(batch);
        if (batch.getStatus() != null && !"ACTIVE".equalsIgnoreCase(batch.getStatus())) {
            throw new IllegalArgumentException(label + " is " + batch.getStatus().toLowerCase(java.util.Locale.ROOT)
                    + " and cannot be sold");
        }
        if (batch.getExpiryDate() != null
                && !batch.getExpiryDate().isAfter(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
            throw new IllegalArgumentException(label + " expired on " + batch.getExpiryDate() + " and cannot be sold");
        }
        if (batch.getSellingPrice() == null || batch.getSellingPrice().signum() <= 0) {
            throw new IllegalArgumentException(label + " has no selling price");
        }
        if (batch.getMrp() != null && batch.getSellingPrice().compareTo(batch.getMrp()) > 0) {
            throw new IllegalArgumentException(label + ": selling price is above MRP");
        }
        if (batch.getCurrentQuantity().compareTo(itemReq.getQuantity()) < 0) {
            throw new IllegalArgumentException("Insufficient stock for batch: " + batch.getBatchNumber());
        }
        return batch;
    }

    /** Deducts the stock of one line, records the movement, and returns the priced sale line. */
    private PharmacySaleItem sellLine(BigDecimal qty, MedicineBatch batch, PharmacyBillCalculator.LineResult money,
                                      PharmacyBillCalculator.Line line, PharmacySale sale, Long hospitalId,
                                      Long branchId, Long userId, List<InventoryTransaction> stockMovements) {
        BigDecimal qtyBefore = batch.getCurrentQuantity();
        batch.setCurrentQuantity(qtyBefore.subtract(qty));
        batchRepository.save(batch);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(hospitalId);
        tx.setBranchId(branchId);
        tx.setMedicineBatchId(batch.getId());
        tx.setTransactionType("SALE");
        tx.setQuantity(qty.negate()); // Negative for sales
        tx.setQuantityBefore(qtyBefore);
        tx.setQuantityAfter(batch.getCurrentQuantity());
        tx.setReferenceType("PHARMACY_SALE");
        tx.setCreatedBy(userId);
        stockMovements.add(tx);

        PharmacySaleItem item = new PharmacySaleItem();
        item.setPharmacySale(sale);
        item.setMedicineId(batch.getMedicineId());
        item.setMedicineBatchId(batch.getId());
        item.setQuantity(qty);
        item.setQuantityRaw(qty);
        item.setUnitPrice(line.unitPrice());
        item.setTaxPercentage(line.gstPercentage());
        item.setTaxPercentageRaw(line.gstPercentage());
        item.setTaxAmount(money.gst());
        item.setDiscountAmount(money.discount());
        item.setDiscountPercentage(money.gross().signum() == 0 ? BigDecimal.ZERO
                : money.discount().multiply(BigDecimal.valueOf(100)).divide(money.gross(), 2, java.math.RoundingMode.HALF_UP));
        item.setTotalAmount(money.net());
        item.setTotalAmountRaw(money.net());
        return item;
    }

    private static BigDecimal gstRateOf(MedicineBatch batch) {
        if (batch.getGstPercentage() != null) {
            return batch.getGstPercentage();
        }
        if (batch.getMedicine() != null && batch.getMedicine().getGstPercentage() != null) {
            return batch.getMedicine().getGstPercentage();
        }
        return BigDecimal.ZERO;
    }

    private static String batchLabel(MedicineBatch batch) {
        String name = batch.getMedicine() != null ? batch.getMedicine().getMedicineName() : null;
        return (name != null ? name + " " : "") + "batch " + batch.getBatchNumber();
    }

    /**
     * Marks the consultation's prescriptions as dispensed, but only a consultation of this same
     * facility: the id comes from the browser, and another facility's prescriptions must never
     * change because of a sale here.
     */
    private void markPrescriptionsDispensed(Long medicalRecordId, Long hospitalId) {
        for (com.hms.entity.Prescription rx : prescriptionRepository.findByMedicalRecordId(medicalRecordId)) {
            if (!hospitalId.equals(rx.getHospitalId())) {
                continue;
            }
            rx.setStatus("DISPENSED");
            prescriptionRepository.save(rx);
        }
    }

    /**
     * What the customer actually paid for one unit of this line: the line's share of the bill's
     * net amount (after discount, GST included), divided by the quantity. A refund is based on
     * this, never on the shelf price, so it can never be more than was paid. It also holds for
     * bills saved before prices included GST, whose line totals did not add up to the net.
     */
    static BigDecimal paidPerUnit(PharmacySale sale, PharmacySaleItem line) {
        BigDecimal lineTotal = line.getTotalAmount() == null ? BigDecimal.ZERO : line.getTotalAmount();
        BigDecimal linesTotal = sale.getItems().stream()
                .map(i -> i.getTotalAmount() == null ? BigDecimal.ZERO : i.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = sale.getNetAmount() == null ? linesTotal : sale.getNetAmount();
        if (linesTotal.signum() == 0 || line.getQuantity() == null || line.getQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return lineTotal.multiply(net).divide(linesTotal.multiply(line.getQuantity()), 6, java.math.RoundingMode.HALF_UP);
    }

    public Page<PharmacySale> getSalesHistory(Pageable pageable) {
        return saleRepository.findScopedHistory(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId(), pageable);
    }

    public PharmacySale getSaleDetails(Long id) {
        return saleRepository.findByIdScoped(id, securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale record not found"));
    }

    public BigDecimal getTodaySalesTotal() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return saleRepository.sumSalesBetween(securityHelper.getCurrentHospitalId(),
                securityHelper.getCurrentBranchId(), startOfDay, LocalDateTime.now());
    }

    /** Today's sales count and total, for this branch when a branch is signed in. */
    public java.util.Map<String, Object> getDashboardStats() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("todaySalesTotal", saleRepository.sumSalesBetween(hospitalId, branchId, startOfDay, now));
        stats.put("todaySalesCount", saleRepository.countSalesBetween(hospitalId, branchId, startOfDay, now));
        return stats;
    }

    public PharmacySale findByBillNumber(String billNumber, Long hospitalId) {
        return saleRepository.findByBillNumberScoped(billNumber, hospitalId, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + billNumber));
    }

    @Transactional
    public java.util.Map<String, Object> processPatientReturn(Long saleId, List<java.util.Map<String, Object>> returnItems) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        PharmacySale sale = saleRepository.findByIdScoped(saleId, hospitalId, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy sale invoice not found"));

        BigDecimal refundTotal = BigDecimal.ZERO;

        for (java.util.Map<String, Object> item : returnItems) {
            Long batchId = Long.valueOf(item.get("medicineBatchId").toString());
            BigDecimal qtyToReturn = new BigDecimal(item.get("quantityToReturn").toString());

            if (qtyToReturn.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive");
            }

            // Find matching sold item in original sale to validate
            PharmacySaleItem soldItem = sale.getItems().stream()
                    .filter(i -> i.getMedicineBatchId().equals(batchId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Medicine batch was not part of original invoice"));

            // The bound is what is STILL returnable, not what was originally sold. Checked and
            // taken in one statement: a read-then-write would let two returns submitted together
            // each see the same remaining quantity and both refund it.
            if (saleItemRepository.claimReturn(soldItem.getId(), qtyToReturn) != 1) {
                java.math.BigDecimal already = soldItem.getReturnedQuantity() == null
                        ? java.math.BigDecimal.ZERO : soldItem.getReturnedQuantity();
                java.math.BigDecimal remaining = soldItem.getQuantity().subtract(already);
                throw new IllegalArgumentException(
                        "Cannot return " + qtyToReturn + " for batch " + batchId
                                + ": only " + remaining.max(java.math.BigDecimal.ZERO)
                                + " of the " + soldItem.getQuantity() + " sold remain returnable.");
            }

            // Expiry safety check: Reject returns if the medicine batch has expired
            // Tenant- and branch-scoped. This was an unscoped findById, which let a caller name
            // another facility's batch id; with the restock branch gone it is the only batch
            // lookup left on this path, so it has to be the safe one.
            MedicineBatch batchToCheck = batchRepository
                    .findByIdAndHospitalIdForUpdate(batchId, hospitalId, securityHelper.getCurrentBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));
            if (batchToCheck.getExpiryDate() != null && batchToCheck.getExpiryDate().isBefore(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("Cannot return medicine '" + 
                    (batchToCheck.getMedicine() != null ? batchToCheck.getMedicine().getMedicineName() : batchToCheck.getBatchNumber()) + 
                    "' because the batch has already expired on " + batchToCheck.getExpiryDate() + "!");
            }

            BigDecimal refundValue = paidPerUnit(sale, soldItem).multiply(qtyToReturn)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            refundTotal = refundTotal.add(refundValue);

            // The money itself, so reports can show refunds and the GST they take back.
            com.hms.entity.pharmacy.PharmacySaleReturn refund = new com.hms.entity.pharmacy.PharmacySaleReturn();
            refund.setHospitalId(hospitalId);
            refund.setBranchId(securityHelper.getCurrentBranchId());
            refund.setSaleId(sale.getId());
            refund.setSaleItemId(soldItem.getId());
            refund.setMedicineBatchId(batchId);
            refund.setQuantity(qtyToReturn);
            refund.setRefundAmount(refundValue);
            BigDecimal rate = soldItem.getTaxPercentage() == null ? BigDecimal.ZERO : soldItem.getTaxPercentage();
            refund.setGstAmount(refundValue.subtract(refundValue.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(100).add(rate), 2, java.math.RoundingMode.HALF_UP)));
            refund.setCreatedBy(userId);
            saleReturnRepository.save(refund);

            // Medicine that has been in a patient's possession does not go back on the shelf.
            //
            // This used to be the caller's choice: a `restock` flag in the request body decided
            // whether the quantity was added straight back to the saleable batch. Nothing
            // verified how the medicine had been stored, or whether it was still the medicine
            // that left, and a client that sent `true` put it back into the pool the next
            // patient is dispensed from. There is no quarantine model yet, so the honest
            // behaviour is to refund the money and leave saleable stock alone.
            //
            // The ledger row says exactly that: quantity zero, balance unchanged. It records
            // that a return happened without asserting that stock increased.
            InventoryTransaction tx = new InventoryTransaction();
            tx.setHospitalId(hospitalId);
            tx.setBranchId(securityHelper.getCurrentBranchId());
            tx.setMedicineBatchId(batchId);
            tx.setTransactionType("RETURN");
            tx.setQuantity(BigDecimal.ZERO);
            tx.setQuantityBefore(batchToCheck.getCurrentQuantity());
            tx.setQuantityAfter(batchToCheck.getCurrentQuantity());
            tx.setReferenceType("PHARMACY_SALE");
            tx.setReferenceId(sale.getId());
            tx.setRemarks("Patient return for Bill #" + sale.getBillNumber()
                    + " - refunded, not returned to saleable stock");
            tx.setCreatedBy(userId);
            transactionRepository.save(tx);
        }

        auditTrail.record("PHARMACY_REFUND", "PHARMACY_SALE", sale.getBillNumber(),
                "Refunded ₹" + refundTotal.toPlainString() + " on bill " + sale.getBillNumber());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "SUCCESS");
        result.put("refundAmount", refundTotal);
        result.put("message", "Patient return and refund processed successfully!");
        return result;
    }
}

