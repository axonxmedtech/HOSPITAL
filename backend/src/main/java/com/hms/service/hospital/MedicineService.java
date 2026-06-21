package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.MedicineList;
import com.hms.repository.MedicineRepository;
import com.hms.repository.MedicineListRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MedicineService {

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineListRepository medicineListRepository;

    @Autowired
    private com.hms.repository.MedicinePurchaseRepository medicinePurchaseRepository;

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
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return medicineListRepository.searchByName("%" + query + "%", hospitalId);
    }

    public List<MedicineList> getCatalogMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return medicineListRepository.findByHospitalId(hospitalId);
    }

    public MedicineList addCatalogMedicine(MedicineList medicine) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        if (medicineListRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            throw new IllegalArgumentException("Medicine already exists in catalog");
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
            throw new UnauthorizedException("Unauthorized access to catalog medicine");
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

        // Sync catalog updates to existing active stock inventory
        medicineRepository.findByNameIgnoreCaseAndHospitalId(saved.getName(), hospitalId).ifPresent(stock -> {
            stock.setDefaultDosage(saved.getDefaultDosage());
            stock.setDefaultFrequency(saved.getDefaultFrequency());
            stock.setDefaultDuration(saved.getDefaultDuration());
            medicineRepository.save(stock);
        });

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
            throw new UnauthorizedException("Unauthorized access to catalog medicine");
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

    @Transactional
    public Map<String, Object> importCatalogCsv(MultipartFile file) throws Exception {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        int imported = 0, updated = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                // Skip header row
                if (lineNum == 1 && trimmed.toLowerCase().startsWith("name")) continue;

                String[] cols = trimmed.split(",", -1);
                String name = stripQuotes(cols[0]);
                if (name.isEmpty()) continue;

                String type = cols.length > 1 ? stripQuotes(cols[1]) : "";
                if (type.isEmpty()) type = "Tablet";
                String dosage = cols.length > 2 ? stripQuotes(cols[2]) : "";
                String freq = cols.length > 3 ? stripQuotes(cols[3]) : "";
                String duration = cols.length > 4 ? stripQuotes(cols[4]) : "";
                String manufacturer = cols.length > 5 ? stripQuotes(cols[5]) : "";

                try {
                    Optional<MedicineList> existing = medicineListRepository.findByNameIgnoreCaseAndHospitalId(name, hospitalId);
                    if (existing.isPresent()) {
                        MedicineList m = existing.get();
                        if (!type.isEmpty()) m.setType(type);
                        if (!dosage.isEmpty()) m.setDefaultDosage(dosage);
                        if (!freq.isEmpty()) m.setDefaultFrequency(freq);
                        if (!duration.isEmpty()) m.setDefaultDuration(duration);
                        if (!manufacturer.isEmpty()) m.setManufacturer(manufacturer);
                        m.setIsActive(true);
                        medicineListRepository.save(m);
                        updated++;
                    } else {
                        MedicineList m = new MedicineList();
                        m.setName(name);
                        m.setType(type);
                        m.setDefaultDosage(dosage.isEmpty() ? null : dosage);
                        m.setDefaultFrequency(freq.isEmpty() ? null : freq);
                        m.setDefaultDuration(duration.isEmpty() ? null : duration);
                        m.setManufacturer(manufacturer.isEmpty() ? null : manufacturer);
                        m.setHospitalId(hospitalId);
                        m.setIsActive(true);
                        medicineListRepository.save(m);
                        imported++;
                    }
                } catch (Exception e) {
                    errors.add("Row " + lineNum + " (" + name + "): " + e.getMessage());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("errors", errors);
        return result;
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.trim();
    }

    private Optional<MedicineList> getCatalogMatch(String name, Long hospitalId) {
        if (name == null || hospitalId == null) {
            return Optional.empty();
        }
        List<MedicineList> matches = medicineListRepository.findByNameIgnoreCaseAndHospitalOrGlobal(name.trim(), hospitalId);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        // Prefer hospital-specific over global fallback
        return matches.stream()
                .filter(m -> m.getHospitalId() != null)
                .findFirst()
                .or(() -> matches.stream().findFirst());
    }

    // --- Purchase History Management ---

    public List<com.hms.entity.MedicinePurchase> getMedicinePurchases() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return medicinePurchaseRepository.findByHospitalIdOrderByPurchaseDateDesc(hospitalId);
    }

    @Transactional
    public com.hms.entity.MedicinePurchase addMedicinePurchase(com.hms.entity.MedicinePurchase purchase) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        purchase.setHospitalId(hospitalId);
        com.hms.entity.MedicinePurchase savedPurchase = medicinePurchaseRepository.save(purchase);

        // Find existing active stock by name
        Optional<Medicine> existingOpt = medicineRepository.findByNameIgnoreCaseAndHospitalId(purchase.getName(), hospitalId);
        
        Medicine stock;
        if (existingOpt.isPresent()) {
            stock = existingOpt.get();
            stock.setStockQuantity(stock.getStockQuantity() + purchase.getQuantity());
            stock.setUnitPrice(purchase.getUnitPrice());
            stock.setExpiryDate(purchase.getExpiryDate());
            stock.setManufacturer(purchase.getManufacturer());
            stock.setMinStockLevel(purchase.getMinStockLevel());
            stock.setType(purchase.getType());
            stock.setDefaultDosage(purchase.getDefaultDosage());
            stock.setDefaultFrequency(purchase.getDefaultFrequency());
            stock.setDefaultDuration(purchase.getDefaultDuration());
            stock.setIsActive(true);
        } else {
            stock = new Medicine();
            stock.setName(purchase.getName());
            stock.setStockQuantity(purchase.getQuantity());
            stock.setUnitPrice(purchase.getUnitPrice());
            stock.setExpiryDate(purchase.getExpiryDate());
            stock.setMinStockLevel(purchase.getMinStockLevel());
            stock.setType(purchase.getType());
            stock.setManufacturer(purchase.getManufacturer());
            stock.setDefaultDosage(purchase.getDefaultDosage());
            stock.setDefaultFrequency(purchase.getDefaultFrequency());
            stock.setDefaultDuration(purchase.getDefaultDuration());
            stock.setHospitalId(hospitalId);
            stock.setIsActive(true);
        }
        medicineRepository.save(stock);

        // Auto-catalog medicine in lookup dictionary if it does not exist
        if (!medicineListRepository.existsByNameAndHospitalId(purchase.getName(), hospitalId)) {
            MedicineList newCatalog = new MedicineList();
            newCatalog.setName(purchase.getName());
            newCatalog.setType(purchase.getType() != null ? purchase.getType() : "Tablet");
            newCatalog.setDefaultDosage(purchase.getDefaultDosage());
            newCatalog.setDefaultFrequency(purchase.getDefaultFrequency());
            newCatalog.setDefaultDuration(purchase.getDefaultDuration());
            newCatalog.setManufacturer(purchase.getManufacturer());
            newCatalog.setHospitalId(hospitalId);
            medicineListRepository.save(newCatalog);
        }

        // Audit Log
        try {
            auditLogService.logAction(
                    "MEDICINE_PURCHASE_ADDED",
                    "Recorded purchase of " + savedPurchase.getName() + " (Qty: " + savedPurchase.getQuantity() + ", Cost: ₹" + savedPurchase.getUnitPrice() + ")",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "MEDICINE",
                    savedPurchase.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception ignored) {}

        return savedPurchase;
    }

    // --- Active Stock Inventory CRUD ---

    public List<Medicine> getInventoryMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        List<Medicine> list = medicineRepository.findByHospitalId(hospitalId);
        for (Medicine m : list) {
            if (m.getDefaultDosage() == null || m.getDefaultDosage().trim().isEmpty() ||
                m.getDefaultFrequency() == null || m.getDefaultFrequency().trim().isEmpty() ||
                m.getDefaultDuration() == null || m.getDefaultDuration().trim().isEmpty()) {
                
                getCatalogMatch(m.getName(), hospitalId).ifPresent(catalog -> {
                    if (m.getDefaultDosage() == null || m.getDefaultDosage().trim().isEmpty()) {
                        m.setDefaultDosage(catalog.getDefaultDosage());
                    }
                    if (m.getDefaultFrequency() == null || m.getDefaultFrequency().trim().isEmpty()) {
                        m.setDefaultFrequency(catalog.getDefaultFrequency());
                    }
                    if (m.getDefaultDuration() == null || m.getDefaultDuration().trim().isEmpty()) {
                        m.setDefaultDuration(catalog.getDefaultDuration());
                    }
                });
            }
        }
        return list;
    }

    @Transactional
    public Medicine addInventoryMedicine(Medicine medicine) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        // Prevent duplicates in active physical stock inventory
        if (medicineRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            throw new IllegalArgumentException("Medicine already exists in stock inventory");
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
        } else {
            // Copy defaults from catalog if medicine defaults are blank
            getCatalogMatch(medicine.getName(), hospitalId).ifPresent(catalog -> {
                if (medicine.getDefaultDosage() == null || medicine.getDefaultDosage().trim().isEmpty()) {
                    medicine.setDefaultDosage(catalog.getDefaultDosage());
                }
                if (medicine.getDefaultFrequency() == null || medicine.getDefaultFrequency().trim().isEmpty()) {
                    medicine.setDefaultFrequency(catalog.getDefaultFrequency());
                }
                if (medicine.getDefaultDuration() == null || medicine.getDefaultDuration().trim().isEmpty()) {
                    medicine.setDefaultDuration(catalog.getDefaultDuration());
                }
            });
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
            throw new UnauthorizedException("Unauthorized access to stock inventory");
        }

        Integer oldStock = medicine.getStockQuantity();
        medicine.setName(request.getName());
        medicine.setStockQuantity(request.getStockQuantity());
        medicine.setUnitPrice(request.getUnitPrice());
        medicine.setMinStockLevel(request.getMinStockLevel());
        medicine.setExpiryDate(request.getExpiryDate());
        medicine.setType(request.getType());
        
        String dosage = request.getDefaultDosage();
        String freq = request.getDefaultFrequency();
        String dur = request.getDefaultDuration();

        if (dosage == null || dosage.trim().isEmpty() ||
            freq == null || freq.trim().isEmpty() ||
            dur == null || dur.trim().isEmpty()) {
            Optional<MedicineList> catalogOpt = getCatalogMatch(request.getName(), hospitalId);
            if (catalogOpt.isPresent()) {
                MedicineList catalog = catalogOpt.get();
                if (dosage == null || dosage.trim().isEmpty()) {
                    dosage = catalog.getDefaultDosage();
                }
                if (freq == null || freq.trim().isEmpty()) {
                    freq = catalog.getDefaultFrequency();
                }
                if (dur == null || dur.trim().isEmpty()) {
                    dur = catalog.getDefaultDuration();
                }
            }
        }

        medicine.setDefaultDosage(dosage);
        medicine.setDefaultFrequency(freq);
        medicine.setDefaultDuration(dur);
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
            throw new UnauthorizedException("Unauthorized access to stock inventory");
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

