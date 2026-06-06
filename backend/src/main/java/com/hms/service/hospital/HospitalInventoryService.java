package com.hms.service.hospital;

import com.hms.entity.HospitalInventory;
import com.hms.entity.InventoryItem;
import com.hms.repository.HospitalInventoryRepository;
import com.hms.repository.InventoryItemRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HospitalInventoryService {

    @Autowired
    private HospitalInventoryRepository hospitalInventoryRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    // --- Catalog Lookup CRUD ---

    public List<InventoryItem> searchInventoryCatalog(String query) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return inventoryItemRepository.searchByName(query, hospitalId);
    }

    public List<InventoryItem> getCatalogItems() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return inventoryItemRepository.findByHospitalId(hospitalId);
    }

    public InventoryItem addCatalogItem(InventoryItem item) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (inventoryItemRepository.existsByNameAndHospitalId(item.getName(), hospitalId)) {
            throw new RuntimeException("Item already exists in catalog");
        }

        item.setHospitalId(hospitalId);
        // Preserve linkedFeeId if provided
        InventoryItem saved = inventoryItemRepository.save(item);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_ITEM_ADDED",
                    "Added " + saved.getName() + " to hospital inventory catalog",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        return saved;
    }

    public InventoryItem updateCatalogItem(Long id, InventoryItem request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        InventoryItem catalog = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog item not found"));

        if (catalog.getHospitalId() != null && !catalog.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to catalog item");
        }

        catalog.setName(request.getName());
        catalog.setType(request.getType());
        catalog.setManufacturer(request.getManufacturer());
        catalog.setLinkedFeeId(request.getLinkedFeeId()); // persist fee link
        if (request.getIsActive() != null) {
            catalog.setIsActive(request.getIsActive());
        }

        InventoryItem saved = inventoryItemRepository.save(catalog);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_ITEM_UPDATED",
                    "Updated catalog item " + saved.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        return saved;
    }

    public void deleteCatalogItem(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        InventoryItem catalog = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog item not found"));

        if (catalog.getHospitalId() != null && !catalog.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to catalog item");
        }

        catalog.setIsActive(false);
        inventoryItemRepository.save(catalog);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_ITEM_DEACTIVATED",
                    "Deactivated catalog item " + catalog.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    catalog.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}
    }

    // --- Active Stock Inventory CRUD ---

    public List<HospitalInventory> getInventoryItems() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return hospitalInventoryRepository.findByHospitalId(hospitalId);
    }

    @Transactional
    public HospitalInventory addInventoryItem(HospitalInventory stock) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (hospitalInventoryRepository.existsByNameAndHospitalId(stock.getName(), hospitalId)) {
            throw new RuntimeException("Item already exists in stock inventory");
        }

        // Auto-catalog if it doesn't exist
        if (!inventoryItemRepository.existsByNameAndHospitalId(stock.getName(), hospitalId)) {
            InventoryItem newCatalog = new InventoryItem();
            newCatalog.setName(stock.getName());
            newCatalog.setType(stock.getType() != null ? stock.getType() : "Consumable");
            newCatalog.setManufacturer(stock.getManufacturer());
            newCatalog.setHospitalId(hospitalId);
            inventoryItemRepository.save(newCatalog);
        }

        stock.setHospitalId(hospitalId);
        HospitalInventory saved = hospitalInventoryRepository.save(stock);

        // Audit Log
        try {
            auditLogService.logAction(
                    "INVENTORY_ITEM_RESTOCKED",
                    "Added " + saved.getName() + " to active stock inventory. Quantity: " + saved.getStockQuantity(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception ignored) {}

        return saved;
    }

    @Transactional
    public HospitalInventory updateInventoryItem(Long id, HospitalInventory request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        HospitalInventory stock = hospitalInventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock inventory record not found"));

        if (!stock.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to stock inventory");
        }

        Integer oldStock = stock.getStockQuantity();
        stock.setName(request.getName());
        stock.setStockQuantity(request.getStockQuantity());
        stock.setUnitPrice(request.getUnitPrice());
        stock.setMinStockLevel(request.getMinStockLevel());
        stock.setExpiryDate(request.getExpiryDate());
        stock.setType(request.getType());
        stock.setManufacturer(request.getManufacturer());
        if (request.getIsActive() != null) {
            stock.setIsActive(request.getIsActive());
        }

        HospitalInventory saved = hospitalInventoryRepository.save(stock);

        // Audit Log
        try {
            auditLogService.logAction(
                    "INVENTORY_ITEM_MODIFIED",
                    "Modified " + saved.getName() + " stock from " + oldStock + " to " + saved.getStockQuantity(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception ignored) {}

        return saved;
    }

    @Transactional
    public void deleteInventoryItem(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        HospitalInventory stock = hospitalInventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock inventory record not found"));

        if (!stock.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to stock inventory");
        }

        stock.setIsActive(false);
        hospitalInventoryRepository.save(stock);

        // Audit Log
        try {
            auditLogService.logAction(
                    "INVENTORY_ITEM_DEACTIVATED",
                    "Deactivated active stock inventory record for " + stock.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    stock.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception ignored) {}
    }
}
