package com.hms.controller.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.MedicineList;
import com.hms.security.RequireModule;
import com.hms.service.hospital.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hospital/medicines")
public class MedicineController {

    @Autowired
    private MedicineService medicineService;

    // --- Search Autocomplete Endpoint ---
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<List<MedicineList>> searchMedicines(@RequestParam String query) {
        return ResponseEntity.ok(medicineService.searchMedicines(query));
    }

    // --- Catalog Lookup CRUD ---

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<MedicineList>> getCatalogMedicines() {
        return ResponseEntity.ok(medicineService.getCatalogMedicines());
    }

    @PostMapping("/catalog")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> addCatalogMedicine(@RequestBody MedicineList catalog) {
        return ResponseEntity.ok(medicineService.addCatalogMedicine(catalog));
    }

    @PutMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateCatalogMedicine(@PathVariable Long id, @RequestBody MedicineList catalog) {
        return ResponseEntity.ok(medicineService.updateCatalogMedicine(id, catalog));
    }

    @PostMapping("/catalog/import-csv")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> importCatalogCsv(@RequestParam("file") MultipartFile file) throws Exception {
        Map<String, Object> result = medicineService.importCatalogCsv(file);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteCatalogMedicine(@PathVariable Long id) {
        medicineService.deleteCatalogMedicine(id);
        return ResponseEntity.ok().build();
    }

    // --- Purchase History Management ---

    @GetMapping("/purchases")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<List<com.hms.entity.MedicinePurchase>> getMedicinePurchases() {
        return ResponseEntity.ok(medicineService.getMedicinePurchases());
    }

    @PostMapping("/purchases")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<?> addMedicinePurchase(@RequestBody com.hms.entity.MedicinePurchase purchase) {
        return ResponseEntity.ok(medicineService.addMedicinePurchase(purchase));
    }

    // --- Active Stock Inventory CRUD ---

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<List<Medicine>> getInventoryMedicines() {
        return ResponseEntity.ok(medicineService.getInventoryMedicines());
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<?> addInventoryMedicine(@RequestBody Medicine stock) {
        return ResponseEntity.ok(medicineService.addInventoryMedicine(stock));
    }

    @PutMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<?> updateInventoryMedicine(@PathVariable Long id, @RequestBody Medicine stock) {
        return ResponseEntity.ok(medicineService.updateInventoryMedicine(id, stock));
    }

    @DeleteMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @RequireModule("MEDICAL_INVENTORY")
    public ResponseEntity<?> deleteInventoryMedicine(@PathVariable Long id) {
        medicineService.deleteInventoryMedicine(id);
        return ResponseEntity.ok().build();
    }

    // Legacy fallback
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> addMedicine(@RequestBody Medicine medicine) {
        return ResponseEntity.ok(medicineService.addMedicine(medicine));
    }
}
