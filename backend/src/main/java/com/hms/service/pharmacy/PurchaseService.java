package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.PurchaseRequest;
import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.entity.pharmacy.MedicineMaster;
import com.hms.entity.pharmacy.PurchaseInvoice;
import com.hms.entity.pharmacy.PurchaseInvoiceItem;
import com.hms.entity.pharmacy.PurchasePayment;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.repository.pharmacy.MedicineMasterRepository;
import com.hms.repository.pharmacy.PurchaseInvoiceRepository;
import com.hms.repository.pharmacy.PurchasePaymentRepository;
import com.hms.repository.pharmacy.SupplierRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Purchases from suppliers: a DRAFT can be edited or discarded; POSTED adds the stock; a posted
 * purchase can be cancelled (with a reason) only while all of its stock is still on the shelf;
 * payments to the supplier are recorded against it.
 *
 * <p>Amounts are worked out here from the lines (rate excludes GST; GST is added; the invoice
 * discount comes off the total). Totals sent by the browser are ignored.
 */
@Service
public class PurchaseService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseService.class);
    private static final String POSTED = "POSTED";
    private static final String DRAFT = "DRAFT";
    private static final String CANCELLED = "CANCELLED";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final Set<String> PAYMENT_METHODS = Set.of("CASH", "UPI", "CARD", "BANK_TRANSFER", "CHEQUE");

    @Autowired private PurchaseInvoiceRepository invoiceRepository;
    @Autowired private MedicineBatchRepository batchRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private HospitalWebSocketHandler webSocketHandler;
    @Autowired private MedicineMasterRepository medicineMasterRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchasePaymentRepository paymentRepository;
    @Autowired private PharmacyAuditTrail auditTrail;

    // ── create / edit ───────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseInvoice createPurchase(PurchaseRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setHospitalId(hospitalId);
        invoice.setBranchId(securityHelper.getCurrentBranchId());
        invoice.setPaymentStatus("PENDING");
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setCreatedBy(securityHelper.getCurrentUserId());
        invoice.setItems(new ArrayList<>());
        fill(invoice, req);

        PurchaseInvoice saved = invoiceRepository.save(invoice);
        if (POSTED.equals(saved.getPostingStatus())) {
            addToStock(saved);
            broadcastRefresh(hospitalId);
        }
        return saved;
    }

    /** Replaces a DRAFT's header and lines. A posted purchase cannot be edited: cancel it and enter it again. */
    @Transactional
    public PurchaseInvoice updateDraft(Long id, PurchaseRequest req) {
        PurchaseInvoice invoice = lock(id);
        if (!DRAFT.equals(invoice.getPostingStatus())) {
            throw new IllegalArgumentException("Only a draft can be edited. A posted purchase can be cancelled and entered again.");
        }
        invoice.getItems().clear();
        fill(invoice, req);
        PurchaseInvoice saved = invoiceRepository.save(invoice);
        if (POSTED.equals(saved.getPostingStatus())) {
            addToStock(saved);
            broadcastRefresh(saved.getHospitalId());
        }
        return saved;
    }

    /** Header, lines and server-side totals from the request. */
    private void fill(PurchaseInvoice invoice, PurchaseRequest req) {
        Long hospitalId = invoice.getHospitalId();
        if (req.getSupplierId() == null || supplierRepository.findByIdScoped(req.getSupplierId(), hospitalId,
                securityHelper.getCurrentBranchId()).isEmpty()) {
            throw new IllegalArgumentException("Choose a supplier of this pharmacy");
        }
        String invoiceNumber = req.getInvoiceNumber() == null ? "" : req.getInvoiceNumber().trim();
        if (invoiceNumber.isEmpty()) {
            throw new IllegalArgumentException("Enter the supplier's invoice number");
        }
        if (invoiceRepository.invoiceNumberTaken(hospitalId, req.getSupplierId(), invoiceNumber, invoice.getId())) {
            throw new IllegalArgumentException("Invoice " + invoiceNumber + " from this supplier is already entered. "
                    + "Entering it twice would add the stock twice.");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the purchase");
        }
        String status = req.getPostingStatus() == null || req.getPostingStatus().isBlank() ? DRAFT : req.getPostingStatus();

        invoice.setSupplierId(req.getSupplierId());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceDate(req.getInvoiceDate());
        invoice.setPostingStatus(status);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gst = BigDecimal.ZERO;
        for (PurchaseRequest.PurchaseItemRequest itemReq : req.getItems()) {
            validatePurchaseItem(itemReq);
            PurchaseInvoiceItem item = buildInvoiceItem(itemReq, invoice);
            invoice.getItems().add(item);
            BigDecimal base = itemReq.getQuantity().multiply(itemReq.getPurchaseRate());
            BigDecimal lineGst = base.multiply(rateOf(itemReq)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            item.setLineTotal(base.add(lineGst).setScale(2, RoundingMode.HALF_UP));
            subtotal = subtotal.add(base);
            gst = gst.add(lineGst);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = req.getDiscountAmount() == null ? BigDecimal.ZERO : req.getDiscountAmount().setScale(2, RoundingMode.HALF_UP);
        if (discount.signum() < 0 || discount.compareTo(subtotal.add(gst)) > 0) {
            throw new IllegalArgumentException("Discount must be between 0 and the invoice total");
        }
        invoice.setSubtotal(subtotal);
        invoice.setGstAmount(gst);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(subtotal.add(gst).subtract(discount));
    }

    private static BigDecimal rateOf(PurchaseRequest.PurchaseItemRequest itemReq) {
        return itemReq.getGstPercentage() == null ? BigDecimal.ZERO : itemReq.getGstPercentage();
    }

    private void validatePurchaseItem(PurchaseRequest.PurchaseItemRequest itemReq) {
        validatePurchaseItemPricing(itemReq);
        validatePurchaseItemExpiryAndGst(itemReq);
        String batch = itemReq.getBatchNumber() == null ? "" : itemReq.getBatchNumber().trim();
        if (batch.isEmpty() || "UNBATCHED".equalsIgnoreCase(batch)) {
            throw new IllegalArgumentException("Enter the batch number printed on the pack for every medicine");
        }
        itemReq.setBatchNumber(batch);
    }

    private void validatePurchaseItemPricing(PurchaseRequest.PurchaseItemRequest itemReq) {
        if (itemReq.getQuantity() == null || itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase quantity must be positive");
        }
        if (itemReq.getFreeQuantity() == null || itemReq.getFreeQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Free quantity cannot be negative");
        }
        if (itemReq.getMrp() == null || itemReq.getMrp().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("MRP must be greater than zero");
        }
        if (itemReq.getPurchaseRate() == null || itemReq.getPurchaseRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase rate must be greater than zero");
        }
        if (itemReq.getSellingPrice() == null || itemReq.getSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Selling price must be greater than zero");
        }
        if (itemReq.getSellingPrice().compareTo(itemReq.getMrp()) > 0) {
            throw new IllegalArgumentException("Selling price cannot exceed MRP");
        }
        if (itemReq.getPurchaseRate().compareTo(itemReq.getMrp()) > 0) {
            throw new IllegalArgumentException("Purchase rate cannot exceed MRP");
        }
    }

    private void validatePurchaseItemExpiryAndGst(PurchaseRequest.PurchaseItemRequest itemReq) {
        if (itemReq.getExpiryDate() == null) {
            throw new IllegalArgumentException("Expiry date is required");
        }
        if (itemReq.getExpiryDate().isBefore(LocalDate.now(ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("Expiry date cannot be in the past");
        }
        if (itemReq.getGstPercentage() != null && (itemReq.getGstPercentage().compareTo(BigDecimal.ZERO) < 0
                || itemReq.getGstPercentage().compareTo(HUNDRED) > 0)) {
            throw new IllegalArgumentException("GST percentage must be between 0% and 100%");
        }
    }

    private PurchaseInvoiceItem buildInvoiceItem(PurchaseRequest.PurchaseItemRequest itemReq, PurchaseInvoice invoice) {
        PurchaseInvoiceItem item = new PurchaseInvoiceItem();
        item.setMedicineId(resolveMedicineId(itemReq, invoice.getHospitalId()));
        item.setBatchNumber(itemReq.getBatchNumber());
        item.setExpiryDate(itemReq.getExpiryDate());
        item.setQuantity(itemReq.getQuantity());
        item.setFreeQuantity(itemReq.getFreeQuantity());
        item.setPurchaseRate(itemReq.getPurchaseRate());
        item.setMrp(itemReq.getMrp());
        item.setSellingPrice(itemReq.getSellingPrice());
        item.setGstPercentage(itemReq.getGstPercentage());
        item.setPurchaseInvoice(invoice);
        return item;
    }

    /**
     * Resolve the local MedicineMaster id for a purchase line. If the client sent an
     * explicit medicineId it must be a medicine of this facility. Otherwise find-or-create
     * a MedicineMaster for this hospital from the platform-sourced name/type plus the
     * free-text manufacturer — the standalone pharmacy has no Medicine Master tab, so
     * medicines enter the catalog through purchases.
     */
    private Long resolveMedicineId(PurchaseRequest.PurchaseItemRequest itemReq, Long hospitalId) {
        String hsn = itemReq.getHsnCode() == null || itemReq.getHsnCode().isBlank() ? null : itemReq.getHsnCode().trim();
        if (itemReq.getMedicineId() != null) {
            MedicineMaster own = medicineMasterRepository.findByIdAndHospitalId(itemReq.getMedicineId(), hospitalId)
                    .orElseThrow(() -> new IllegalArgumentException("Medicine not found in this pharmacy"));
            if (hsn != null && (own.getHsnCode() == null || own.getHsnCode().isBlank())) {
                own.setHsnCode(hsn);
                medicineMasterRepository.save(own);
            }
            return own.getId();
        }
        String name = itemReq.getMedicineName() != null ? itemReq.getMedicineName().trim() : null;
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Medicine name is required");
        }
        return medicineMasterRepository.findFirstScopedByName(hospitalId, securityHelper.getCurrentBranchId(), name)
                .map(MedicineMaster::getId)
                .orElseGet(() -> {
                    MedicineMaster m = new MedicineMaster();
                    m.setHospitalId(hospitalId);
                    m.setBranchId(securityHelper.getCurrentBranchId());
                    m.setMedicineName(name);
                    m.setMedicineType(itemReq.getMedicineType());
                    m.setManufacturerName(itemReq.getManufacturerName());
                    m.setHsnCode(hsn);
                    if (itemReq.getGstPercentage() != null) {
                        m.setGstPercentage(itemReq.getGstPercentage());
                    }
                    MedicineMaster saved = medicineMasterRepository.save(m);
                    saved.setMedicineCode("MED" + (1000 + saved.getId()));
                    return medicineMasterRepository.save(saved).getId();
                });
    }

    // ── post / cancel ──────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseInvoice postInvoice(Long id) {
        PurchaseInvoice invoice = lock(id);
        if (!DRAFT.equals(invoice.getPostingStatus())) {
            throw new IllegalArgumentException(POSTED.equals(invoice.getPostingStatus())
                    ? "Invoice already posted" : "A cancelled purchase cannot be posted");
        }
        invoice.setPostingStatus(POSTED);
        PurchaseInvoice saved = invoiceRepository.save(invoice);
        addToStock(saved);
        broadcastRefresh(saved.getHospitalId());
        return saved;
    }

    /**
     * Cancels a purchase with a reason. A draft is simply discarded. A posted purchase takes its
     * stock back out, which is only possible while every unit it brought in is still on the
     * shelf; once some were sold, a supplier return is the way to send the rest back.
     */
    @Transactional
    public PurchaseInvoice cancel(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw new IllegalArgumentException("Give a reason for cancelling this purchase");
        }
        PurchaseInvoice invoice = lock(id);
        if (CANCELLED.equals(invoice.getPostingStatus())) {
            throw new IllegalArgumentException("This purchase is already cancelled");
        }
        if (invoice.getPaidAmount() != null && invoice.getPaidAmount().signum() > 0) {
            throw new IllegalArgumentException("Payments are recorded against this purchase; it cannot be cancelled");
        }
        boolean wasPosted = POSTED.equals(invoice.getPostingStatus());
        if (wasPosted) {
            takeOutOfStock(invoice);
        }
        invoice.setPostingStatus(CANCELLED);
        invoice.setCancelReason(reason.trim());
        invoice.setCancelledAt(LocalDateTime.now(ZoneId.systemDefault()));
        invoice.setCancelledBy(securityHelper.getCurrentUserId());
        PurchaseInvoice saved = invoiceRepository.save(invoice);
        auditTrail.record(wasPosted ? "PURCHASE_CANCELLED" : "PURCHASE_DRAFT_DISCARDED", "PURCHASE_INVOICE",
                invoice.getInvoiceNumber(), "Purchase invoice " + invoice.getInvoiceNumber()
                        + (wasPosted ? " cancelled; its stock was taken back out" : " discarded (draft)"), reason.trim());
        broadcastRefresh(saved.getHospitalId());
        return saved;
    }

    private void addToStock(PurchaseInvoice invoice) {
        Long hospitalId = invoice.getHospitalId();
        auditTrail.record("PURCHASE_POSTED", "PURCHASE_INVOICE", invoice.getInvoiceNumber(),
                "Purchase invoice " + invoice.getInvoiceNumber() + " posted to stock: " + invoice.getItems().size()
                        + " line(s), total ₹" + (invoice.getTotalAmount() == null ? "0" : invoice.getTotalAmount().toPlainString()));
        for (PurchaseInvoiceItem item : invoice.getItems()) {
            MedicineBatch batch = batchRepository.findByHospitalIdAndMedicineIdAndBatchNumberForUpdate(
                    hospitalId, invoice.getBranchId(), item.getMedicineId(), item.getBatchNumber())
                    .orElse(new MedicineBatch());
            BigDecimal qtyBefore = batch.getCurrentQuantity() != null ? batch.getCurrentQuantity() : BigDecimal.ZERO;
            BigDecimal inward = inwardOf(item);

            if (batch.getId() == null) {
                batch.setHospitalId(hospitalId);
                batch.setBranchId(invoice.getBranchId());
                batch.setMedicineId(item.getMedicineId());
                batch.setBatchNumber(item.getBatchNumber());
                batch.setExpiryDate(item.getExpiryDate());
                batch.setStatus("ACTIVE");
            } else {
                refuseConflictingBatch(batch, item);
            }
            batch.setCurrentQuantity(qtyBefore.add(inward));
            // Prices follow the latest purchase of the batch; the expiry never changes.
            batch.setMrp(item.getMrp());
            batch.setPurchaseRate(item.getPurchaseRate());
            batch.setSellingPrice(item.getSellingPrice());
            batch.setSupplierId(invoice.getSupplierId());
            batch.setPurchaseInvoiceItemId(item.getId());
            batch.setGstPercentage(item.getGstPercentage());
            MedicineBatch savedBatch = batchRepository.save(batch);

            ledger(invoice, savedBatch, "PURCHASE", inward, qtyBefore, "Purchase Inward: Inv #" + invoice.getInvoiceNumber());
        }
    }

    /**
     * The same batch number arriving again is the same physical batch: it must carry the same
     * expiry, and a batch that was blocked or disposed must not quietly come back into sale.
     */
    private static void refuseConflictingBatch(MedicineBatch batch, PurchaseInvoiceItem item) {
        if (batch.getStatus() != null && !"ACTIVE".equalsIgnoreCase(batch.getStatus())) {
            throw new IllegalArgumentException("Batch " + batch.getBatchNumber() + " was "
                    + batch.getStatus().toLowerCase(Locale.ROOT) + " earlier and cannot be restocked. Check the batch number.");
        }
        if (batch.getExpiryDate() != null && !batch.getExpiryDate().equals(item.getExpiryDate())) {
            throw new IllegalArgumentException("Batch " + batch.getBatchNumber() + " is already in stock with expiry "
                    + batch.getExpiryDate() + ", not " + item.getExpiryDate() + ". Check the batch number and expiry.");
        }
    }

    private void takeOutOfStock(PurchaseInvoice invoice) {
        for (PurchaseInvoiceItem item : invoice.getItems()) {
            MedicineBatch batch = batchRepository.findByHospitalIdAndMedicineIdAndBatchNumberForUpdate(
                    invoice.getHospitalId(), invoice.getBranchId(), item.getMedicineId(), item.getBatchNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Batch " + item.getBatchNumber() + " is no longer in stock"));
            BigDecimal inward = inwardOf(item);
            BigDecimal onShelf = batch.getCurrentQuantity() == null ? BigDecimal.ZERO : batch.getCurrentQuantity();
            if (onShelf.compareTo(inward) < 0) {
                throw new IllegalArgumentException("Only " + onShelf.stripTrailingZeros().toPlainString() + " of the "
                        + inward.stripTrailingZeros().toPlainString() + " units of batch " + item.getBatchNumber()
                        + " are still in stock (the rest were sold or returned). Use a supplier return instead.");
            }
            batch.setCurrentQuantity(onShelf.subtract(inward));
            batchRepository.save(batch);
            ledger(invoice, batch, "PURCHASE_CANCELLED", inward.negate(), onShelf,
                    "Purchase cancelled: Inv #" + invoice.getInvoiceNumber());
        }
    }

    private static BigDecimal inwardOf(PurchaseInvoiceItem item) {
        BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal free = item.getFreeQuantity() != null ? item.getFreeQuantity() : BigDecimal.ZERO;
        return qty.add(free);
    }

    private void ledger(PurchaseInvoice invoice, MedicineBatch batch, String type, BigDecimal change,
                        BigDecimal before, String remarks) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(invoice.getHospitalId());
        tx.setBranchId(invoice.getBranchId());
        tx.setMedicineBatchId(batch.getId());
        tx.setTransactionType(type);
        tx.setQuantity(change);
        tx.setQuantityBefore(before);
        tx.setQuantityAfter(batch.getCurrentQuantity());
        tx.setReferenceType("PURCHASE_INVOICE");
        tx.setReferenceId(invoice.getId());
        tx.setRemarks(remarks);
        tx.setCreatedBy(securityHelper.getCurrentUserId());
        transactionRepository.save(tx);
    }

    // ── supplier payments ──────────────────────────────────────────────────────────────────

    /** Records a payment to the supplier against a posted purchase; never more than the balance. */
    @Transactional
    public PurchaseInvoice recordPayment(Long id, BigDecimal amount, String method, String reference, LocalDate paidOn) {
        PurchaseInvoice invoice = lock(id);
        if (!POSTED.equals(invoice.getPostingStatus())) {
            throw new IllegalArgumentException("Payments can be recorded only against a posted purchase");
        }
        String m = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_METHODS.contains(m)) {
            throw new IllegalArgumentException("Payment method must be Cash, UPI, Card, Bank transfer or Cheque");
        }
        BigDecimal paid = invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount();
        BigDecimal balance = invoice.getTotalAmount().subtract(paid);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Enter the amount paid");
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Only ₹" + balance.toPlainString() + " is still due on this invoice");
        }
        LocalDate day = paidOn == null ? LocalDate.now(ZoneId.systemDefault()) : paidOn;
        if (day.isAfter(LocalDate.now(ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("The payment date cannot be in the future");
        }

        PurchasePayment p = new PurchasePayment();
        p.setHospitalId(invoice.getHospitalId());
        p.setBranchId(invoice.getBranchId());
        p.setPurchaseInvoiceId(invoice.getId());
        p.setAmount(amt);
        p.setMethod(m);
        p.setReference(reference == null || reference.isBlank() ? null : reference.trim());
        p.setPaidOn(day);
        p.setCreatedBy(securityHelper.getCurrentUserId());
        paymentRepository.save(p);

        BigDecimal newPaid = paid.add(amt);
        invoice.setPaidAmount(newPaid);
        invoice.setPaymentStatus(newPaid.compareTo(invoice.getTotalAmount()) >= 0 ? "PAID" : "PARTIAL");
        PurchaseInvoice saved = invoiceRepository.save(invoice);
        auditTrail.record("SUPPLIER_PAYMENT", "PURCHASE_INVOICE", invoice.getInvoiceNumber(),
                "Paid ₹" + amt.toPlainString() + " (" + m + ") against invoice " + invoice.getInvoiceNumber()
                        + "; balance ₹" + invoice.getTotalAmount().subtract(newPaid).toPlainString());
        return saved;
    }

    public List<PurchasePayment> payments(Long id) {
        PurchaseInvoice invoice = getInvoice(id);
        return paymentRepository.findByPurchaseInvoiceIdAndHospitalIdOrderByPaidOnAscIdAsc(invoice.getId(), invoice.getHospitalId());
    }

    // ── reads ──────────────────────────────────────────────────────────────────────────────

    public Page<PurchaseInvoice> listInvoices(Pageable pageable) {
        return invoiceRepository.findScopedHistory(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId(), pageable);
    }

    public PurchaseInvoice getInvoice(Long id) {
        return invoiceRepository.findByIdScoped(id, securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private PurchaseInvoice lock(Long id) {
        return invoiceRepository.lockScoped(id, securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private void broadcastRefresh(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after a purchase change", e);
        }
    }
}
