package com.hms.service;

import com.hms.entity.DepartmentIndent;
import com.hms.entity.HospitalInventory;
import com.hms.entity.InventoryItem;
import com.hms.entity.StockTransaction;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.GeneralInventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralInventoryServiceTest {

    @Mock private DepartmentIndentRepository indentRepository;
    @Mock private StockTransactionRepository transactionRepository;
    @Mock private HospitalInventoryRepository inventoryRepository;
    @Mock private InventoryItemRepository itemRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private GeneralInventoryService service;

    private void stubTenant(Long hospitalId) {
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
    }

    @Test
    void raiseIndent_savesAndDefaultsToPending() {
        Long hospitalId = 1L, itemId = 5L;
        stubTenant(hospitalId);

        InventoryItem item = new InventoryItem();
        item.setId(itemId);
        item.setHospitalId(hospitalId);
        item.setName("Gloves");
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(indentRepository.save(any(DepartmentIndent.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentIndent indent = service.raiseIndent("Ward A", itemId, BigDecimal.valueOf(10));

        assertThat(indent.getStatus()).isEqualTo("PENDING");
        assertThat(indent.getRequestedQty()).isEqualTo(BigDecimal.valueOf(10));
        assertThat(indent.getFromDepartment()).isEqualTo("Ward A");
    }

    @Test
    void issueStock_blocksNegativeStock() {
        Long hospitalId = 1L, indentId = 10L, batchId = 20L;
        stubTenant(hospitalId);

        DepartmentIndent indent = new DepartmentIndent();
        indent.setId(indentId);
        indent.setHospitalId(hospitalId);
        indent.setInventoryItemId(5L);
        indent.setFromDepartment("Ward A");
        when(indentRepository.findByIdAndHospitalId(indentId, hospitalId)).thenReturn(Optional.of(indent));

        HospitalInventory batch = new HospitalInventory();
        batch.setId(batchId);
        batch.setHospitalId(hospitalId);
        batch.setStockQuantity(5); // only 5 available
        batch.setExpiryDate(LocalDate.now().plusDays(10));
        when(inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.issueStock(indentId, batchId, BigDecimal.valueOf(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds available stock");
    }

    @Test
    void issueStock_blocksExpiredBatch() {
        Long hospitalId = 1L, indentId = 10L, batchId = 20L;
        stubTenant(hospitalId);

        DepartmentIndent indent = new DepartmentIndent();
        indent.setId(indentId);
        indent.setHospitalId(hospitalId);
        indent.setInventoryItemId(5L);
        indent.setFromDepartment("Ward A");
        when(indentRepository.findByIdAndHospitalId(indentId, hospitalId)).thenReturn(Optional.of(indent));

        HospitalInventory batch = new HospitalInventory();
        batch.setId(batchId);
        batch.setHospitalId(hospitalId);
        batch.setStockQuantity(100);
        batch.setExpiryDate(LocalDate.now().minusDays(1)); // expired
        when(inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.issueStock(indentId, batchId, BigDecimal.valueOf(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired batch");
    }

    @Test
    void issueStock_updatesStockAndCreatesTransaction() {
        Long hospitalId = 1L, indentId = 10L, batchId = 20L;
        stubTenant(hospitalId);

        DepartmentIndent indent = new DepartmentIndent();
        indent.setId(indentId);
        indent.setHospitalId(hospitalId);
        indent.setInventoryItemId(5L);
        indent.setFromDepartment("Ward A");
        when(indentRepository.findByIdAndHospitalId(indentId, hospitalId)).thenReturn(Optional.of(indent));

        HospitalInventory batch = new HospitalInventory();
        batch.setId(batchId);
        batch.setHospitalId(hospitalId);
        batch.setStockQuantity(100);
        batch.setExpiryDate(LocalDate.now().plusDays(10));
        when(inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)).thenReturn(Optional.of(batch));

        when(indentRepository.save(any(DepartmentIndent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.save(any(HospitalInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentIndent result = service.issueStock(indentId, batchId, BigDecimal.valueOf(10));

        assertThat(result.getStatus()).isEqualTo("FILLED");
        assertThat(batch.getStockQuantity()).isEqualTo(90);
        verify(transactionRepository, times(1)).save(any(StockTransaction.class));
    }

    @Test
    void suggestFefo_sortsExpiryAscendingWithNullsLast() {
        Long hospitalId = 1L, itemId = 5L;
        stubTenant(hospitalId);

        InventoryItem item = new InventoryItem();
        item.setId(itemId);
        item.setName("Sutures");
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        HospitalInventory batchNull = new HospitalInventory();
        batchNull.setId(1L);
        batchNull.setExpiryDate(null);

        HospitalInventory batchFar = new HospitalInventory();
        batchFar.setId(2L);
        batchFar.setExpiryDate(LocalDate.now().plusDays(100));

        HospitalInventory batchNear = new HospitalInventory();
        batchNear.setId(3L);
        batchNear.setExpiryDate(LocalDate.now().plusDays(10));

        List<HospitalInventory> batches = new ArrayList<>();
        batches.add(batchNull);
        batches.add(batchFar);
        batches.add(batchNear);

        when(inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue("Sutures", hospitalId)).thenReturn(batches);

        List<HospitalInventory> sorted = service.suggestFefo(itemId);

        assertThat(sorted.get(0).getId()).isEqualTo(3L); // near first
        assertThat(sorted.get(1).getId()).isEqualTo(2L); // far second
        assertThat(sorted.get(2).getId()).isEqualTo(1L); // null last
    }
}
