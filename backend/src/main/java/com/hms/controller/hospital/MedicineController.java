package com.hms.controller.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.MedicineList;
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
@CrossOrigin(origins = "*")
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
        try {
            return ResponseEntity.ok(medicineService.addCatalogMedicine(catalog));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateCatalogMedicine(@PathVariable Long id, @RequestBody MedicineList catalog) {
        try {
            return ResponseEntity.ok(medicineService.updateCatalogMedicine(id, catalog));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/catalog/import-csv")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> importCatalogCsv(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = medicineService.importCatalogCsv(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/catalog/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteCatalogMedicine(@PathVariable Long id) {
        try {
            medicineService.deleteCatalogMedicine(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- Active Stock Inventory CRUD ---

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<List<Medicine>> getInventoryMedicines() {
        return ResponseEntity.ok(medicineService.getInventoryMedicines());
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> addInventoryMedicine(@RequestBody Medicine stock) {
        try {
            return ResponseEntity.ok(medicineService.addInventoryMedicine(stock));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateInventoryMedicine(@PathVariable Long id, @RequestBody Medicine stock) {
        try {
            return ResponseEntity.ok(medicineService.updateInventoryMedicine(id, stock));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> deleteInventoryMedicine(@PathVariable Long id) {
        try {
            medicineService.deleteInventoryMedicine(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Legacy fallback
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> addMedicine(@RequestBody Medicine medicine) {
        try {
            return ResponseEntity.ok(medicineService.addMedicine(medicine));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
