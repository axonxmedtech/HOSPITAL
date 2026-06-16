package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.PurchaseRequest;
import com.hms.entity.pharmacy.*;
import com.hms.repository.pharmacy.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PurchaseService {

    @Autowired
    private PurchaseInvoiceRepository invoiceRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public PurchaseInvoice createPurchase(PurchaseRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setHospitalId(hospitalId);
        invoice.setSupplierId(req.getSupplierId());
        invoice.setInvoiceNumber(req.getInvoiceNumber());
        invoice.setInvoiceDate(req.getInvoiceDate());
        invoice.setSubtotal(req.getSubtotal());
        invoice.setDiscountAmount(req.getDiscountAmount());
        invoice.setGstAmount(req.getGstAmount());
        invoice.setTotalAmount(req.getTotalAmount());
        invoice.setPostingStatus(req.getPostingStatus());
        invoice.setPaymentStatus("PENDING");
        invoice.setCreatedBy(securityHelper.getCurrentUserId());

        List<PurchaseInvoiceItem> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (PurchaseRequest.PurchaseItemRequest itemReq : req.getItems()) {
                PurchaseInvoiceItem item = new PurchaseInvoiceItem();
                item.setMedicineId(itemReq.getMedicineId());
                item.setBatchNumber(itemReq.getBatchNumber());
                item.setExpiryDate(itemReq.getExpiryDate());
                item.setQuantity(itemReq.getQuantity());
                item.setFreeQuantity(itemReq.getFreeQuantity());
                item.setPurchaseRate(itemReq.getPurchaseRate());
                item.setMrp(itemReq.getMrp());
                item.setSellingPrice(itemReq.getSellingPrice());
                item.setGstPercentage(itemReq.getGstPercentage());
                item.setLineTotal(itemReq.getLineTotal());
                item.setPurchaseInvoice(invoice);
                items.add(item);
            }
        }
        invoice.setItems(items);
        
        PurchaseInvoice saved = invoiceRepository.save(invoice);

        if ("POSTED".equalsIgnoreCase(saved.getPostingStatus())) {
            updateInventory(saved);
            try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); } catch (Exception ignored) {}
        }

        return saved;
    }

    @Transactional
    public PurchaseInvoice postInvoice(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PurchaseInvoice invoice = invoiceRepository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if ("POSTED".equalsIgnoreCase(invoice.getPostingStatus())) {
            throw new RuntimeException("Invoice already posted");
        }

        invoice.setPostingStatus("POSTED");
        PurchaseInvoice saved = invoiceRepository.save(invoice);
        updateInventory(saved);
        try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); } catch (Exception ignored) {}
        return saved;
    }

    private void updateInventory(PurchaseInvoice invoice) {
        Long hospitalId = invoice.getHospitalId();
        for (PurchaseInvoiceItem item : invoice.getItems()) {
            // 1. Find or create batch with Pessimistic Lock
            MedicineBatch batch = batchRepository.findByHospitalIdAndMedicineIdAndBatchNumberForUpdate(
                    hospitalId, item.getMedicineId(), item.getBatchNumber())
                    .orElse(new MedicineBatch());

            BigDecimal qtyBefore = batch.getCurrentQuantity() != null ? batch.getCurrentQuantity() : BigDecimal.ZERO;
            BigDecimal purchaseQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            BigDecimal freeQty = item.getFreeQuantity() != null ? item.getFreeQuantity() : BigDecimal.ZERO;
            BigDecimal totalInward = purchaseQty.add(freeQty);

            if (batch.getId() == null) {
                batch.setHospitalId(hospitalId);
                batch.setMedicineId(item.getMedicineId());
                batch.setBatchNumber(item.getBatchNumber());
                batch.setCurrentQuantity(totalInward);
            } else {
                batch.setCurrentQuantity(qtyBefore.add(totalInward));
            }

            // Update prices/dates from latest purchase
            batch.setExpiryDate(item.getExpiryDate());
            batch.setMrp(item.getMrp());
            batch.setPurchaseRate(item.getPurchaseRate());
            batch.setSellingPrice(item.getSellingPrice());
            batch.setSupplierId(invoice.getSupplierId());
            batch.setPurchaseInvoiceItemId(item.getId());
            batch.setGstPercentage(item.getGstPercentage());
            batch.setStatus("ACTIVE");

            MedicineBatch savedBatch = batchRepository.save(batch);

            // 2. Record Transaction
            InventoryTransaction tx = new InventoryTransaction();
            tx.setHospitalId(hospitalId);
            tx.setMedicineBatchId(savedBatch.getId());
            tx.setTransactionType("PURCHASE");
            tx.setQuantity(totalInward);
            tx.setQuantityBefore(qtyBefore);
            tx.setQuantityAfter(savedBatch.getCurrentQuantity());
            tx.setReferenceType("PURCHASE_INVOICE");
            tx.setReferenceId(invoice.getId());
            tx.setRemarks("Purchase Inward: Inv #" + invoice.getInvoiceNumber());
            transactionRepository.save(tx);
        }
    }

    public Page<PurchaseInvoice> listInvoices(Pageable pageable) {
        return invoiceRepository.findByHospitalIdOrderByCreatedAtDesc(securityHelper.getCurrentHospitalId(), pageable);
    }

    public PurchaseInvoice getInvoice(Long id) {
        return invoiceRepository.findByIdAndHospitalId(id, securityHelper.getCurrentHospitalId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }
}
