package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.MedicineList;
import com.hms.repository.MedicineRepository;
import com.hms.repository.MedicineListRepository;
import com.hms.security.SecurityContextHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class MedicineService {

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineListRepository medicineListRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    // --- Master Catalog Search & CRUD ---

    public List<MedicineList> searchMedicines(String query) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return medicineListRepository.searchByName("%" + query + "%", hospitalId);
    }

    public List<MedicineList> getCatalogMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return medicineListRepository.findByHospitalId(hospitalId);
    }

    public MedicineList addCatalogMedicine(MedicineList medicine) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (medicineListRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            throw new RuntimeException("Medicine already exists in catalog");
        }

        medicine.setHospitalId(hospitalId);
        MedicineList saved = medicineListRepository.save(medicine);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_MEDICINE_ADDED",
                    "Added " + saved.getName() + " to master catalog",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        return saved;
    }

    public MedicineList updateCatalogMedicine(Long id, MedicineList request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        MedicineList catalog = medicineListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog medicine not found"));

        if (catalog.getHospitalId() != null && !catalog.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to catalog medicine");
        }

        catalog.setName(request.getName());
        catalog.setType(request.getType());
        catalog.setDefaultDosage(request.getDefaultDosage());
        catalog.setDefaultFrequency(request.getDefaultFrequency());
        catalog.setDefaultDuration(request.getDefaultDuration());
        catalog.setManufacturer(request.getManufacturer());
        if (request.getIsActive() != null) {
            catalog.setIsActive(request.getIsActive());
        }

        MedicineList saved = medicineListRepository.save(catalog);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_MEDICINE_UPDATED",
                    "Updated catalog medicine " + saved.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        return saved;
    }

    public void deleteCatalogMedicine(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        MedicineList catalog = medicineListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog medicine not found"));

        if (catalog.getHospitalId() != null && !catalog.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to catalog medicine");
        }

        catalog.setIsActive(false);
        medicineListRepository.save(catalog);

        // Audit Log
        try {
            auditLogService.logAction(
                    "CATALOG_MEDICINE_DEACTIVATED",
                    "Deactivated catalog medicine " + catalog.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    catalog.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}
    }

    // --- Active Stock Inventory CRUD ---

    public List<Medicine> getInventoryMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return medicineRepository.findByHospitalId(hospitalId);
    }

    @Transactional
    public Medicine addInventoryMedicine(Medicine medicine) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Prevent duplicates in active physical stock inventory
        if (medicineRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            throw new RuntimeException("Medicine already exists in stock inventory");
        }

        // --- Suggestion 2: Auto-catalog if it doesn't exist ---
        if (!medicineListRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            MedicineList newCatalog = new MedicineList();
            newCatalog.setName(medicine.getName());
            newCatalog.setType(medicine.getType() != null ? medicine.getType() : "Tablet");
            newCatalog.setDefaultDosage(medicine.getDefaultDosage());
            newCatalog.setDefaultFrequency(medicine.getDefaultFrequency());
            newCatalog.setDefaultDuration(medicine.getDefaultDuration());
            newCatalog.setManufacturer(medicine.getManufacturer());
            newCatalog.setHospitalId(hospitalId);
            medicineListRepository.save(newCatalog);
        }

        medicine.setHospitalId(hospitalId);
        Medicine saved = medicineRepository.save(medicine);

        // --- Suggestion 3: Audit Log ---
        try {
            auditLogService.logAction(
                    "INVENTORY_RESTOCKED",
                    "Added " + saved.getName() + " to active stock inventory. Quantity: " + saved.getStockQuantity(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            // ignore
        }

        return saved;
    }

    @Transactional
    public Medicine updateInventoryMedicine(Long id, Medicine request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock inventory record not found"));

        if (!medicine.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to stock inventory");
        }

        Integer oldStock = medicine.getStockQuantity();
        medicine.setName(request.getName());
        medicine.setStockQuantity(request.getStockQuantity());
        medicine.setUnitPrice(request.getUnitPrice());
        medicine.setMinStockLevel(request.getMinStockLevel());
        medicine.setExpiryDate(request.getExpiryDate());
        medicine.setType(request.getType());
        medicine.setDefaultDosage(request.getDefaultDosage());
        medicine.setDefaultFrequency(request.getDefaultFrequency());
        medicine.setDefaultDuration(request.getDefaultDuration());
        medicine.setManufacturer(request.getManufacturer());
        if (request.getIsActive() != null) {
            medicine.setIsActive(request.getIsActive());
        }

        Medicine saved = medicineRepository.save(medicine);

        // --- Suggestion 3: Audit Log ---
        try {
            auditLogService.logAction(
                    "INVENTORY_MODIFIED",
                    "Modified " + saved.getName() + " stock from " + oldStock + " to " + saved.getStockQuantity(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    saved.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            // ignore
        }

        return saved;
    }

    @Transactional
    public void deleteInventoryMedicine(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock inventory record not found"));

        if (!medicine.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to stock inventory");
        }

        medicine.setIsActive(false);
        medicineRepository.save(medicine);

        // --- Suggestion 3: Audit Log ---
        try {
            auditLogService.logAction(
                    "INVENTORY_DEACTIVATED",
                    "Deactivated active stock inventory record for " + medicine.getName(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    medicine.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            // ignore
        }
    }

    // --- Legacy addMedicine compatible endpoint ---
    @Transactional
    public Medicine addMedicine(Medicine medicine) {
        return addInventoryMedicine(medicine);
    }

    // --- Initial Seeds ---

    @PostConstruct
    public void seedMedicines() {
        if (medicineListRepository.count() == 0) {
            System.out.println("Seeding initial medicines into catalog...");
            List<MedicineList> initialCatalog = Arrays.asList(
                    createMedicineCatalog("Paracetamol", "Tablet", "500mg", "1-0-1", "3 Days", "Generic"),
                    createMedicineCatalog("Amoxicillin", "Capsule", "500mg", "1-1-1", "5 Days", "Generic"),
                    createMedicineCatalog("Ibuprofen", "Tablet", "400mg", "1-0-1", "3 Days", "Generic"),
                    createMedicineCatalog("Cetirizine", "Tablet", "10mg", "0-0-1", "3 Days", "Generic"),
                    createMedicineCatalog("Cough Syrup", "Syrup", "10ml", "1-1-1", "5 Days", "Generic"),
                    createMedicineCatalog("Azithromycin", "Tablet", "500mg", "1-0-0", "3 Days", "Generic"),
                    createMedicineCatalog("Metformin", "Tablet", "500mg", "1-0-1", "30 Days", "Generic"),
                    createMedicineCatalog("Amlodipine", "Tablet", "5mg", "1-0-0", "30 Days", "Generic"),
                    createMedicineCatalog("Omeprazole", "Capsule", "20mg", "1-0-0", "7 Days", "Generic"),
                    createMedicineCatalog("Pantoprazole", "Tablet", "40mg", "1-0-0", "7 Days", "Generic"),
                    createMedicineCatalog("Normal Saline 500ml", "Saline", "500ml", "Once", "1 Day", "Generic"),
                    createMedicineCatalog("Ringer Lactate 500ml", "Saline", "500ml", "Once", "1 Day", "Generic"),
                    createMedicineCatalog("Diclofenac Injection", "Injection", "75mg/3ml", "Once", "1 Day", "Generic")
            );
            medicineListRepository.saveAll(initialCatalog);
        }
    }

    private MedicineList createMedicineCatalog(String name, String type, String dosage, String freq, String duration, String manufacturer) {
        MedicineList m = new MedicineList();
        m.setName(name);
        m.setType(type);
        m.setDefaultDosage(dosage);
        m.setDefaultFrequency(freq);
        m.setDefaultDuration(duration);
        m.setManufacturer(manufacturer);
        m.setIsActive(true);
        return m;
    }
}
