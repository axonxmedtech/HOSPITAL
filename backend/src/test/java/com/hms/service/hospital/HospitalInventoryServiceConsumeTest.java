package com.hms.service.hospital;

import com.hms.entity.HospitalInventory;
import com.hms.entity.InventoryItem;
import com.hms.repository.HospitalInventoryPurchaseRepository;
import com.hms.repository.HospitalInventoryRepository;
import com.hms.repository.InventoryItemRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HospitalInventoryServiceConsumeTest {

    @Mock HospitalInventoryRepository hospitalInventoryRepository;
    @Mock InventoryItemRepository inventoryItemRepository;
    @Mock HospitalInventoryPurchaseRepository hospitalInventoryPurchaseRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks HospitalInventoryService service;

    private InventoryItem catalogItem(String name, boolean hasOwnStock, String relativeIds) {
        InventoryItem item = new InventoryItem();
        item.setName(name);
        item.setHospitalId(1L);
        item.setHasOwnStock(hasOwnStock);
        item.setRelativeItemIds(relativeIds);
        return item;
    }

    private HospitalInventory stock(Long id, String name, int qty) {
        HospitalInventory s = new HospitalInventory();
        s.setId(id);
        s.setName(name);
        s.setHospitalId(1L);
        s.setStockQuantity(qty);
        s.setIsActive(true);
        return s;
    }

    @Test
    void consumeChargeableItem_stockedItem_decrementsOwnStockAndCascades() {
        when(inventoryItemRepository.findByNameAndHospitalId("Vitamin B12 Injection", 1L))
                .thenReturn(Optional.of(catalogItem("Vitamin B12 Injection", true, "[]")));
        HospitalInventory ampuleStock = stock(10L, "Vitamin B12 Injection", 5);
        when(hospitalInventoryRepository.findByIdAndHospitalId(10L, 1L)).thenReturn(Optional.of(ampuleStock));

        HospitalInventory result = service.consumeChargeableItem(10L, "Vitamin B12 Injection", 1, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getStockQuantity()).isEqualTo(4);
        verify(hospitalInventoryRepository).save(ampuleStock);
    }

    @Test
    void consumeChargeableItem_stockedItem_insufficientStock_throws() {
        when(inventoryItemRepository.findByNameAndHospitalId("Vitamin B12 Injection", 1L))
                .thenReturn(Optional.of(catalogItem("Vitamin B12 Injection", true, "[]")));
        HospitalInventory ampuleStock = stock(10L, "Vitamin B12 Injection", 0);
        when(hospitalInventoryRepository.findByIdAndHospitalId(10L, 1L)).thenReturn(Optional.of(ampuleStock));

        assertThatThrownBy(() -> service.consumeChargeableItem(10L, "Vitamin B12 Injection", 1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void consumeChargeableItem_stockedItem_missingStockId_throws() {
        when(inventoryItemRepository.findByNameAndHospitalId("Vitamin B12 Injection", 1L))
                .thenReturn(Optional.of(catalogItem("Vitamin B12 Injection", true, "[]")));

        assertThatThrownBy(() -> service.consumeChargeableItem(null, "Vitamin B12 Injection", 1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock ID is required");
    }

    @Test
    void consumeChargeableItem_serviceTypeItem_skipsOwnStockCheck_returnsNull() {
        when(inventoryItemRepository.findByNameAndHospitalId("Dressing", 1L))
                .thenReturn(Optional.of(catalogItem("Dressing", false, "[]")));

        HospitalInventory result = service.consumeChargeableItem(null, "Dressing", 1, 1L);

        assertThat(result).isNull();
        verify(hospitalInventoryRepository, never()).findByIdAndHospitalId(any(), any());
        verify(hospitalInventoryRepository, never()).save(any());
    }

    @Test
    void consumeChargeableItem_serviceTypeItem_stillCascadesToRelativeItems() {
        when(inventoryItemRepository.findByNameAndHospitalId("Dressing", 1L))
                .thenReturn(Optional.of(catalogItem("Dressing", false, "[2]")));
        InventoryItem cotton = catalogItem("Cotton", true, "[]");
        cotton.setId(2L);
        when(inventoryItemRepository.findById(2L)).thenReturn(Optional.of(cotton));
        HospitalInventory cottonStock = stock(20L, "Cotton", 50);
        when(hospitalInventoryRepository.findByNameAndHospitalIdAndIsActiveTrue("Cotton", 1L))
                .thenReturn(new java.util.ArrayList<>(List.of(cottonStock)));

        service.consumeChargeableItem(null, "Dressing", 1, 1L);

        assertThat(cottonStock.getStockQuantity()).isEqualTo(49);
        verify(hospitalInventoryRepository).save(cottonStock);
    }

    @Test
    void consumeChargeableItem_catalogItemNotFound_throws() {
        when(inventoryItemRepository.findByNameAndHospitalId("Unknown Item", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeChargeableItem(null, "Unknown Item", 1, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Catalog item not found");
    }

    @Test
    void consumeChargeableItem_legacyNullHasOwnStock_treatedAsStocked() {
        InventoryItem legacyItem = catalogItem("Legacy Item", true, "[]");
        legacyItem.setHasOwnStock(null); // simulates a row from before this migration ran, if ever possible
        when(inventoryItemRepository.findByNameAndHospitalId("Legacy Item", 1L)).thenReturn(Optional.of(legacyItem));
        HospitalInventory legacyStock = stock(30L, "Legacy Item", 3);
        when(hospitalInventoryRepository.findByIdAndHospitalId(30L, 1L)).thenReturn(Optional.of(legacyStock));

        HospitalInventory result = service.consumeChargeableItem(30L, "Legacy Item", 1, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getStockQuantity()).isEqualTo(2);
    }
}
