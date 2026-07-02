package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcurementService {

    @Autowired
    private PurchaseRequisitionRepository requisitionRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Autowired
    private HospitalInventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogService auditLogService;

    // --- Purchase Requisitions ---

    @Transactional
    public PurchaseRequisition createRequisition(String department, LocalDate requiredDate, String priority, String itemsJson) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPublicId("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pr.setHospitalId(hospitalId);
        pr.setDepartment(department);
        pr.setRequestedBy(1L); // Mock staff user ID or current logged-in user
        pr.setPriority(priority);
        pr.setRequiredDate(requiredDate);
        pr.setItemsJson(itemsJson);
        pr.setStatus("PENDING_APPROVAL");
        pr.setCreatedAt(LocalDateTime.now());

        return requisitionRepository.save(pr);
    }

    @Transactional
    public PurchaseRequisition approveRequisition(Long prId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        PurchaseRequisition pr = requisitionRepository.findByIdAndHospitalId(prId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Requisition not found: " + prId));

        pr.setStatus("APPROVED");
        return requisitionRepository.save(pr);
    }

    public List<PurchaseRequisition> getRequisitions() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return requisitionRepository.findByHospitalId(hospitalId);
    }

    // --- Vendor Management ---

    @Transactional
    public Vendor createVendor(Vendor vendor) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        // Set tenant ID
        vendor.setHospitalId(hospitalId);
        return vendorRepository.save(vendor);
    }

    public List<Vendor> getVendors() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return vendorRepository.findByHospitalId(hospitalId);
    }

    // --- Purchase Orders ---

    @Transactional
    public PurchaseOrder createPurchaseOrder(Long vendorId, LocalDate expectedDelivery, String itemsJson) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        Vendor vendor = vendorRepository.findByIdAndHospitalId(vendorId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        // Generate PO number sequentially
        List<PurchaseOrder> existing = purchaseOrderRepository.findByHospitalId(hospitalId);
        int seqNum = existing.size() + 1;
        String poNumber = "PO-" + LocalDate.now().getYear() + "-" + String.format("%05d", seqNum);

        PurchaseOrder po = new PurchaseOrder();
        po.setHospitalId(hospitalId);
        po.setVendorId(vendorId);
        po.setPoNumber(poNumber);
        po.setExpectedDelivery(expectedDelivery);
        po.setItemsJson(itemsJson);
        po.setStatus("DRAFT");
        po.setOrderDate(LocalDateTime.now());

        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(Long poId, String signature) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        PurchaseOrder po = purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found: " + poId));

        po.setStatus("SENT");
        po.setApprovedBy(1L); // Mock manager ID
        po.setApprovedBySig(signature);
        return purchaseOrderRepository.save(po);
    }

    public List<PurchaseOrder> getPurchaseOrders() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return purchaseOrderRepository.findByHospitalId(hospitalId);
    }

    // --- Goods Receipt Confirmation (GRN) ---

    @Transactional
    public PurchaseOrder confirmGrn(Long poId, List<HospitalInventory> receivedItems) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        PurchaseOrder po = purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found: " + poId));

        // Idempotency guard: a PO already marked RECEIVED has had its stock incremented once
        // already - confirming again would double-count stock for the same delivery.
        if ("RECEIVED".equals(po.getStatus())) {
            throw new IllegalStateException("Purchase Order " + po.getPoNumber() + " has already been received.");
        }

        // BR-3 quantity cap: total quantity received in this GRN must not exceed the PO's
        // total ordered quantity (parsed from itemsJson: [{"itemId","quantity","rate"}]).
        // Matched at the PO-total level rather than per-line-item since receivedItems
        // (HospitalInventory batches) carry no itemId back-reference to itemsJson entries.
        // Do-no-harm: a PO with no/blank itemsJson (legacy data) skips the cap rather than
        // blocking the GRN outright.
        if (po.getItemsJson() != null && !po.getItemsJson().isBlank()) {
            double totalOrderedQty = 0;
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode itemsNode = mapper.readTree(po.getItemsJson());
                if (itemsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : itemsNode) {
                        totalOrderedQty += item.path("quantity").asDouble(0);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Purchase Order " + po.getPoNumber() + " has malformed ordered items and cannot be received against.");
            }

            double totalReceivedQty = 0;
            for (HospitalInventory item : receivedItems) {
                totalReceivedQty += item.getStockQuantity() != null ? item.getStockQuantity() : 0;
            }

            if (totalReceivedQty > totalOrderedQty) {
                throw new IllegalStateException("Received quantity (" + totalReceivedQty + ") exceeds ordered quantity (" + totalOrderedQty + ") for Purchase Order " + po.getPoNumber());
            }
        }

        // Enforce BR-3: automatic stock update
        for (HospitalInventory item : receivedItems) {
            // Find existing inventory item or batch in active warehouse
            List<HospitalInventory> matches = inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(item.getName(), hospitalId);
            HospitalInventory batch = null;
            for (HospitalInventory b : matches) {
                if ((b.getExpiryDate() == null && item.getExpiryDate() == null) ||
                    (b.getExpiryDate() != null && b.getExpiryDate().equals(item.getExpiryDate()))) {
                    batch = b;
                    break;
                }
            }

            if (batch == null) {
                batch = new HospitalInventory();
                batch.setHospitalId(hospitalId);
                batch.setName(item.getName());
                batch.setExpiryDate(item.getExpiryDate());
                batch.setUnitPrice(item.getUnitPrice());
                batch.setStockQuantity(item.getStockQuantity());
                batch.setType(item.getType() != null ? item.getType() : "Consumable");
                batch.setManufacturer(item.getManufacturer());
            } else {
                batch.setStockQuantity(batch.getStockQuantity() + item.getStockQuantity());
            }
            inventoryRepository.save(batch);

            // Record stock GRN transaction
            StockTransaction tx = new StockTransaction();
            tx.setHospitalId(hospitalId);
            tx.setInventoryItemId(0L); // special lookup placeholder
            tx.setBatchId(batch.getId());
            tx.setTransactionType("GRN");
            tx.setQuantity(BigDecimal.valueOf(item.getStockQuantity()));
            tx.setFromStore("vendor");
            tx.setToStore("main_store");
            tx.setPerformedBy(securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "system");
            tx.setTransactionTime(LocalDateTime.now());
            transactionRepository.save(tx);
        }

        po.setStatus("RECEIVED");
        return purchaseOrderRepository.save(po);
    }

    // --- 3-Way Matching & Invoices ---

    @Transactional
    public VendorInvoice verifyInvoice(Long vendorId, String invoiceNumber, BigDecimal amount, Long matchedPoId, Long matchedGrnId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        // BR-5: Duplicate invoice code block
        Optional<VendorInvoice> duplicate = vendorInvoiceRepository.findByHospitalIdAndVendorIdAndInvoiceNumber(
                hospitalId, vendorId, invoiceNumber
        );
        if (duplicate.isPresent()) {
            throw new IllegalStateException("Duplicate vendor invoice detected: " + invoiceNumber);
        }

        // BR-4: Three-way Match validation
        PurchaseOrder po = purchaseOrderRepository.findByIdAndHospitalId(matchedPoId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found for matching: " + matchedPoId));

        if (!"RECEIVED".equals(po.getStatus())) {
            throw new IllegalStateException("PO status must be RECEIVED to verify matched invoice. Current status: " + po.getStatus());
        }

        // BR-4 amount reconciliation: the invoice amount must not exceed the PO's own ordered
        // value (parsed from itemsJson: sum of quantity*rate). A 10% tolerance covers taxes and
        // rounding; anything beyond that means the invoice doesn't reconcile against what was
        // actually ordered and must be rejected rather than silently "matched". Do-no-harm: a PO
        // with no/blank itemsJson (legacy data) skips reconciliation rather than blocking verification.
        if (po.getItemsJson() != null && !po.getItemsJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode itemsNode = mapper.readTree(po.getItemsJson());
                BigDecimal poTotal = BigDecimal.ZERO;
                if (itemsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : itemsNode) {
                        BigDecimal quantity = BigDecimal.valueOf(item.path("quantity").asDouble(0));
                        BigDecimal rate = BigDecimal.valueOf(item.path("rate").asDouble(0));
                        poTotal = poTotal.add(quantity.multiply(rate));
                    }
                }
                BigDecimal tolerance = poTotal.multiply(new BigDecimal("1.10"));
                if (amount != null && amount.compareTo(tolerance) > 0) {
                    throw new IllegalStateException("Invoice amount (" + amount + ") exceeds the matched Purchase Order's ordered value (" + poTotal + ") beyond tolerance.");
                }
            } catch (IllegalStateException ise) {
                throw ise;
            } catch (Exception e) {
                throw new IllegalStateException("Purchase Order " + po.getPoNumber() + " has malformed ordered items and cannot be reconciled against.");
            }
        }

        VendorInvoice invoice = new VendorInvoice();
        invoice.setHospitalId(hospitalId);
        invoice.setVendorId(vendorId);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setAmount(amount);
        invoice.setMatchedPoId(matchedPoId);
        invoice.setMatchedGrnId(matchedGrnId);
        invoice.setStatus("VERIFIED");

        return vendorInvoiceRepository.save(invoice);
    }

    @Transactional
    public VendorInvoice processPayment(Long invoiceId, String mode, BigDecimal amount) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        VendorInvoice invoice = vendorInvoiceRepository.findByIdAndHospitalId(invoiceId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));

        if (!"VERIFIED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice must be VERIFIED before releasing payment.");
        }

        invoice.setStatus("PAID");
        return vendorInvoiceRepository.save(invoice);
    }

    public List<VendorInvoice> getInvoices() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return vendorInvoiceRepository.findByHospitalId(hospitalId);
    }
}
