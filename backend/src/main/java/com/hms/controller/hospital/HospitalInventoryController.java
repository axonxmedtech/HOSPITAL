package com.hms.controller.hospital;

import com.hms.entity.HospitalInventory;
import com.hms.entity.InventoryItem;
import com.hms.service.hospital.HospitalInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/hospital-inventory")
public class HospitalInventoryController {

    @Autowired
    private HospitalInventoryService hospitalInventoryService;

    // --- Search Autocomplete Endpoint ---
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<List<InventoryItem>> searchInventoryCatalog(@RequestParam String query) {
        return ResponseEntity.ok(hospitalInventoryService.searchInventoryCatalog(query));
    }

    // --- Catalog Lookup CRUD ---

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<InventoryItem>> getCatalogItems() {
        return ResponseEntity.ok(hospitalInventoryService.getCatalogItems());
    }

    @PostMapping("/catalog")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> addCatalogItem(@RequestBody InventoryItem catalog) {
        return ResponseEntity.ok(hospitalInventoryService.addCatalogItem(catalog));
    }

    @PutMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateCatalogItem(@PathVariable Long id, @RequestBody InventoryItem catalog) {
        return ResponseEntity.ok(hospitalInventoryService.updateCatalogItem(id, catalog));
    }

    @DeleteMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteCatalogItem(@PathVariable Long id) {
        hospitalInventoryService.deleteCatalogItem(id);
        return ResponseEntity.ok().build();
    }

    // --- Active Stock Inventory CRUD ---

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<HospitalInventory>> getInventoryItems() {
        return ResponseEntity.ok(hospitalInventoryService.getInventoryItems());
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> addInventoryItem(@RequestBody HospitalInventory stock) {
        return ResponseEntity.ok(hospitalInventoryService.addInventoryItem(stock));
    }

    @PutMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateInventoryItem(@PathVariable Long id, @RequestBody HospitalInventory stock) {
        return ResponseEntity.ok(hospitalInventoryService.updateInventoryItem(id, stock));
    }

    @DeleteMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteInventoryItem(@PathVariable Long id) {
        hospitalInventoryService.deleteInventoryItem(id);
        return ResponseEntity.ok().build();
    }
}
