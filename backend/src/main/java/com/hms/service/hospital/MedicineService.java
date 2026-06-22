package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.MedicineList;
import com.hms.event.MedicineDispensedEvent;
import com.hms.repository.MedicineRepository;
import com.hms.repository.MedicineListRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // --- Master Catalog Search & CRUD ---

    public List<MedicineList> searchMedicines(String query) {
        return medicineListRepository.findByNameContainingIgnoreCase(query);
    }

    public List<MedicineList> getCatalogMedicines() {
        return medicineListRepository.findAll();
    }

    public MedicineList addCatalogMedicine(MedicineList medicine) {
        if (medicineListRepository.existsByNameIgnoreCase(medicine.getName())) {
            throw new IllegalArgumentException("Medicine already exists in catalog");
        }
        return medicineListRepository.save(medicine);
    }

    public MedicineList updateCatalogMedicine(Long id, MedicineList request) {
        MedicineList catalog = medicineListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog medicine not found"));

        if (medicineListRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("Medicine with this name already exists");
        }

        catalog.setName(request.getName());
        catalog.setType(request.getType());

        return medicineListRepository.save(catalog);
    }

    public void deleteCatalogMedicine(Long id) {
        MedicineList catalog = medicineListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catalog medicine not found"));
        medicineListRepository.delete(catalog);
    }

    @Transactional
    public Map<String, Object> importCatalogCsv(MultipartFile file) throws Exception {
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

                try {
                    Optional<MedicineList> existing = medicineListRepository.findByNameIgnoreCase(name);
                    if (existing.isPresent()) {
                        MedicineList m = existing.get();
                        m.setType(type);
                        medicineListRepository.save(m);
                        updated++;
                    } else {
                        MedicineList m = new MedicineList();
                        m.setName(name);
                        m.setType(type);
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

    private Optional<MedicineList> getCatalogMatch(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return medicineListRepository.findByNameIgnoreCase(name.trim());
    }

    public org.springframework.data.domain.Page<MedicineList> getPlatformMedicines(String query, org.springframework.data.domain.Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            return medicineListRepository.findByNameContainingIgnoreCase(query, pageable);
        }
        return medicineListRepository.findAll(pageable);
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
        if (!medicineListRepository.existsByNameIgnoreCase(purchase.getName())) {
            MedicineList newCatalog = new MedicineList();
            newCatalog.setName(purchase.getName());
            newCatalog.setType(purchase.getType() != null ? purchase.getType() : "Tablet");
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

        try {
            eventPublisher.publishEvent(new MedicineDispensedEvent(
                    hospitalId, null, savedPurchase.getId()));
        } catch (Exception e) {
            // intentionally silent — WhatsApp failures must not affect dispensing
        }

        return savedPurchase;
    }

    // --- Active Stock Inventory CRUD ---

    public List<Medicine> getInventoryMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return medicineRepository.findByHospitalId(hospitalId);
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
        if (!medicineListRepository.existsByNameIgnoreCase(medicine.getName())) {
            MedicineList newCatalog = new MedicineList();
            newCatalog.setName(medicine.getName());
            newCatalog.setType(medicine.getType() != null ? medicine.getType() : "Tablet");
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

        // No catalog defaults lookup since catalog is global and has only name/type.

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
                    createMedicineCatalog("Paracetamol", "Tablet"),
                    createMedicineCatalog("Amoxicillin", "Capsule"),
                    createMedicineCatalog("Ibuprofen", "Tablet"),
                    createMedicineCatalog("Cetirizine", "Tablet"),
                    createMedicineCatalog("Cough Syrup", "Syrup"),
                    createMedicineCatalog("Azithromycin", "Tablet"),
                    createMedicineCatalog("Metformin", "Tablet"),
                    createMedicineCatalog("Amlodipine", "Tablet"),
                    createMedicineCatalog("Omeprazole", "Capsule"),
                    createMedicineCatalog("Pantoprazole", "Tablet"),
                    createMedicineCatalog("Normal Saline 500ml", "Saline"),
                    createMedicineCatalog("Ringer Lactate 500ml", "Saline"),
                    createMedicineCatalog("Diclofenac Injection", "Injection")
            );
            medicineListRepository.saveAll(initialCatalog);
        }
    }

    private MedicineList createMedicineCatalog(String name, String type) {
        MedicineList m = new MedicineList();
        m.setName(name);
        m.setType(type);
        return m;
    }
}

