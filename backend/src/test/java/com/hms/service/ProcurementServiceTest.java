package com.hms.service;

import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.ProcurementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {

    @Mock private PurchaseRequisitionRepository requisitionRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private VendorInvoiceRepository vendorInvoiceRepository;
    @Mock private HospitalInventoryRepository inventoryRepository;
    @Mock private StockTransactionRepository transactionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ProcurementService service;

    private void stubTenant(Long hospitalId) {
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
    }

    @Test
    void createRequisition_savesWithGeneratedId() {
        Long hospitalId = 1L;
        stubTenant(hospitalId);
        when(requisitionRepository.save(any(PurchaseRequisition.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseRequisition pr = service.createRequisition("OPD", LocalDate.now().plusDays(5), "URGENT", "[]");

        assertThat(pr.getPublicId()).startsWith("PR-");
        assertThat(pr.getPriority()).isEqualTo("URGENT");
        assertThat(pr.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void createPurchaseOrder_createsSequentialPoNumber() {
        Long hospitalId = 1L, vendorId = 5L;
        stubTenant(hospitalId);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setHospitalId(hospitalId);
        when(vendorRepository.findByIdAndHospitalId(vendorId, hospitalId)).thenReturn(Optional.of(vendor));

        // Mock 2 existing POs to verify sequence increments to 3
        PurchaseOrder po1 = new PurchaseOrder();
        PurchaseOrder po2 = new PurchaseOrder();
        when(purchaseOrderRepository.findByHospitalId(hospitalId)).thenReturn(java.util.List.of(po1, po2));

        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder po = service.createPurchaseOrder(vendorId, LocalDate.now().plusDays(10), "[]");

        assertThat(po.getPoNumber()).isEqualTo("PO-" + LocalDate.now().getYear() + "-00003");
        assertThat(po.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void verifyInvoice_blocksDuplicateInvoices() {
        Long hospitalId = 1L, vendorId = 5L;
        stubTenant(hospitalId);

        VendorInvoice duplicate = new VendorInvoice();
        duplicate.setInvoiceNumber("INV-999");
        when(vendorInvoiceRepository.findByHospitalIdAndVendorIdAndInvoiceNumber(hospitalId, vendorId, "INV-999"))
                .thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.verifyInvoice(vendorId, "INV-999", BigDecimal.valueOf(100), 1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate vendor invoice detected");
    }

    @Test
    void confirmGrn_incrementsInventoryAndCreatesTransaction() {
        Long hospitalId = 1L, poId = 50L;
        stubTenant(hospitalId);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setHospitalId(hospitalId);
        po.setStatus("SENT");
        when(purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)).thenReturn(Optional.of(po));

        HospitalInventory item = new HospitalInventory();
        item.setName("LINENS");
        item.setStockQuantity(50);
        item.setExpiryDate(null);
        item.setUnitPrice(10.0);

        when(inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue("LINENS", hospitalId))
                .thenReturn(new ArrayList<>());
        when(inventoryRepository.save(any(HospitalInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder completedPo = service.confirmGrn(poId, java.util.List.of(item));

        assertThat(completedPo.getStatus()).isEqualTo("RECEIVED");
        verify(inventoryRepository, times(1)).save(any(HospitalInventory.class));
        verify(transactionRepository, times(1)).save(any(StockTransaction.class));
    }

    @Test
    void confirmGrn_blockedWhenAlreadyReceived() {
        Long hospitalId = 1L, poId = 51L;
        stubTenant(hospitalId);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setHospitalId(hospitalId);
        po.setStatus("RECEIVED");
        when(purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)).thenReturn(Optional.of(po));

        HospitalInventory item = new HospitalInventory();
        item.setName("LINENS");
        item.setStockQuantity(50);

        assertThatThrownBy(() -> service.confirmGrn(poId, java.util.List.of(item)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been received");
        verify(inventoryRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmGrn_blockedWhenReceivedQtyExceedsOrderedQty() {
        Long hospitalId = 1L, poId = 52L;
        stubTenant(hospitalId);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setHospitalId(hospitalId);
        po.setStatus("SENT");
        po.setItemsJson("[{\"itemId\":1,\"quantity\":10,\"rate\":5.0}]");
        when(purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)).thenReturn(Optional.of(po));

        HospitalInventory item = new HospitalInventory();
        item.setName("LINENS");
        item.setStockQuantity(50);

        assertThatThrownBy(() -> service.confirmGrn(poId, java.util.List.of(item)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds ordered quantity");
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void verifyInvoice_blockedWhenAmountExceedsPoValueBeyondTolerance() {
        Long hospitalId = 1L, vendorId = 5L, poId = 60L;
        stubTenant(hospitalId);

        when(vendorInvoiceRepository.findByHospitalIdAndVendorIdAndInvoiceNumber(hospitalId, vendorId, "INV-100"))
                .thenReturn(Optional.empty());

        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setHospitalId(hospitalId);
        po.setPoNumber("PO-2026-00001");
        po.setStatus("RECEIVED");
        po.setItemsJson("[{\"itemId\":1,\"quantity\":10,\"rate\":5.0}]"); // PO total = 50
        when(purchaseOrderRepository.findByIdAndHospitalId(poId, hospitalId)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> service.verifyInvoice(vendorId, "INV-100", BigDecimal.valueOf(1000), poId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the matched Purchase Order's ordered value");
    }
}
