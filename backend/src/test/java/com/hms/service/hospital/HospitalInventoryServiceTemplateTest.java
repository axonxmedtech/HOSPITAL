package com.hms.service.hospital;

import com.hms.dto.InventoryTemplateDTO;
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

@ExtendWith(MockitoExtension.class)
class HospitalInventoryServiceTemplateTest {

    @Mock HospitalInventoryRepository hospitalInventoryRepository;
    @Mock InventoryItemRepository inventoryItemRepository;
    @Mock HospitalInventoryPurchaseRepository hospitalInventoryPurchaseRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks HospitalInventoryService service;

    @Test
    void getCatalogTemplates_returnsNonEmptyFixedList() {
        List<InventoryTemplateDTO> templates = service.getCatalogTemplates();

        assertThat(templates).isNotEmpty();
        assertThat(templates).anySatisfy(t -> assertThat(t.getName()).isEqualTo("Dressing (Small)"));
    }

    @Test
    void getCatalogTemplates_dressingTemplate_isServiceType() {
        List<InventoryTemplateDTO> templates = service.getCatalogTemplates();

        InventoryTemplateDTO dressing = templates.stream()
                .filter(t -> "Dressing (Small)".equals(t.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(dressing.getHasOwnStock()).isFalse();
        assertThat(dressing.getSuggestedRelativeItemNames()).contains("Cotton", "Bandage");
    }

    @Test
    void getCatalogTemplates_injectionTemplate_isStockedType() {
        List<InventoryTemplateDTO> templates = service.getCatalogTemplates();

        InventoryTemplateDTO injection = templates.stream()
                .filter(t -> "Injection".equals(t.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(injection.getHasOwnStock()).isTrue();
    }

    @Test
    void duplicateCatalogItem_returnsSourceItemFields() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        InventoryItem source = new InventoryItem();
        source.setId(5L);
        source.setName("Dressing (Small)");
        source.setType("Consumable");
        source.setHasOwnStock(false);
        source.setLinkedFeeId(42L);
        source.setRelativeItemIds("[1,2]");
        source.setHospitalId(1L);
        when(inventoryItemRepository.findByIdAndHospitalId(5L, 1L)).thenReturn(Optional.of(source));

        InventoryItem result = service.duplicateCatalogItem(5L);

        assertThat(result.getName()).isEqualTo("Dressing (Small)");
        assertThat(result.getHasOwnStock()).isFalse();
        assertThat(result.getLinkedFeeId()).isEqualTo(42L);
        assertThat(result.getRelativeItemIds()).isEqualTo("[1,2]");
    }

    @Test
    void duplicateCatalogItem_notFoundForHospital_throws() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(inventoryItemRepository.findByIdAndHospitalId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicateCatalogItem(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateCatalogItem_persistsHasOwnStockToggle() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        InventoryItem existing = new InventoryItem();
        existing.setId(5L);
        existing.setName("Dressing");
        existing.setHospitalId(1L);
        existing.setHasOwnStock(true); // currently stocked
        when(inventoryItemRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem request = new InventoryItem();
        request.setName("Dressing");
        request.setType("Consumable");
        request.setHasOwnStock(false); // admin flips it to service-type

        InventoryItem result = service.updateCatalogItem(5L, request);

        assertThat(result.getHasOwnStock()).isFalse();
    }
}
