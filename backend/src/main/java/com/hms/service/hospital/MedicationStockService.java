package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingMedicine;
import com.hms.entity.HospitalInventory;
import com.hms.entity.IpdAdmission;
import com.hms.entity.MedicationAdministration;
import com.hms.entity.Medicine;
import com.hms.exception.ConflictException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.BillingItemRepository;
import com.hms.repository.BillingMedicineRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalInventoryRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.MedicineRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stock and billing for medicines given on the ward.
 *
 * <p>A dose given from stock comes out of the inventory it was ordered from — the medicine
 * inventory by batch (earliest expiry first), or the hospital inventory (saline, IV fluids,
 * consumables) by stock row (earliest expiry first) — and its price goes on the admission's IPD
 * bill. A dose from the patient's own supply touches neither. An undone dose puts back exactly
 * what it took and removes its charge.
 */
@Service
public class MedicationStockService {

    public static final String SOURCE_MEDICINE = "MEDICINE";
    public static final String SOURCE_HOSPITAL_ITEM = "HOSPITAL_ITEM";
    private static final String MOVEMENT_TYPE = "IPD_ADMINISTRATION";
    private static final String REFERENCE_TYPE = "MAR";

    /** What a dose took, for the administration record. */
    public record Taken(BigDecimal charge, Long billingItemId, Long billingMedicineId, String detail) {}

    private final MedicineStockService medicineStock;
    private final MedicineRepository medicineRepository;
    private final HospitalInventoryRepository inventoryRepository;
    private final BillingRepository billingRepository;
    private final BillingItemRepository billingItemRepository;
    private final BillingMedicineRepository billingMedicineRepository;
    private final BillingService billingService;
    private final HospitalRepository hospitalRepository;
    private final SecurityContextHelper securityHelper;

    public MedicationStockService(MedicineStockService medicineStock, MedicineRepository medicineRepository,
                                  HospitalInventoryRepository inventoryRepository, BillingRepository billingRepository,
                                  BillingItemRepository billingItemRepository,
                                  BillingMedicineRepository billingMedicineRepository, BillingService billingService,
                                  HospitalRepository hospitalRepository, SecurityContextHelper securityHelper) {
        this.medicineStock = medicineStock;
        this.medicineRepository = medicineRepository;
        this.inventoryRepository = inventoryRepository;
        this.billingRepository = billingRepository;
        this.billingItemRepository = billingItemRepository;
        this.billingMedicineRepository = billingMedicineRepository;
        this.billingService = billingService;
        this.hospitalRepository = hospitalRepository;
        this.securityHelper = securityHelper;
    }

    // ─── search and alerts ────────────────────────────────────────────────

    /**
     * Medicines and hospital items matching {@code query}, each with what is in stock and whether
     * it is running low — for ordering and for giving an item that was not ordered.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String query) {
        Long hospitalId = requireHospitalId();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MedicineStockService.StockOption o : medicineStock.dispensableOptions(needle)) {
            Medicine m = medicineRepository.findByIdAndHospitalId(o.medicineId(), hospitalId).orElse(null);
            int min = m != null && m.getMinStockLevel() != null ? m.getMinStockLevel() : 0;
            out.add(option(SOURCE_MEDICINE, o.medicineId(), o.name(), o.type(), o.availableQuantity(), min,
                    m != null && m.getUnitPrice() != null ? BigDecimal.valueOf(m.getUnitPrice()) : null, o.earliestExpiry()));
        }
        for (Map.Entry<String, List<HospitalInventory>> e : itemsByName(hospitalId).entrySet()) {
            if (!needle.isEmpty() && !e.getKey().toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(itemOption(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Everything at or below its reorder level, in both inventories. Empty items first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lowStock() {
        List<Map<String, Object>> low = new ArrayList<>(search("").stream()
                .filter(m -> Boolean.TRUE.equals(m.get("low"))).toList());
        low.sort(Comparator.comparing((Map<String, Object> m) -> ((Number) m.get("available")).intValue())
                .thenComparing(m -> String.valueOf(m.get("name"))));
        return low;
    }

    /** Stock of the item an order is given from, or null when the order is not stocked. */
    @Transactional(readOnly = true)
    public Map<String, Object> stockFor(String source, Long medicineId, String itemName) {
        Long hospitalId = requireHospitalId();
        if (SOURCE_MEDICINE.equals(source) && medicineId != null) {
            Medicine m = medicineRepository.findByIdAndHospitalId(medicineId, hospitalId).orElse(null);
            if (m == null) return null;
            int available = medicineStock.availableQuantityFor(medicineId, hospitalId);
            return option(SOURCE_MEDICINE, medicineId, m.getName(), m.getType(), available,
                    m.getMinStockLevel() == null ? 0 : m.getMinStockLevel(),
                    m.getUnitPrice() == null ? null : BigDecimal.valueOf(m.getUnitPrice()),
                    medicineStock.earliestUsableExpiry(medicineId, hospitalId));
        }
        if (SOURCE_HOSPITAL_ITEM.equals(source) && itemName != null) {
            List<HospitalInventory> rows = inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(itemName, hospitalId);
            return rows.isEmpty() ? null : itemOption(itemName, rows);
        }
        return null;
    }

    // ─── take and put back ────────────────────────────────────────────────

    /**
     * Takes {@code quantity} of the item from stock for a dose and bills it.
     *
     * @throws ConflictException when there is not enough — the message offers the patient's own
     *                           supply instead, so a missing stock never blocks the dose itself
     */
    @Transactional
    public Taken take(IpdAdmission admission, String source, Long medicineId, String itemName, int quantity, Long marId) {
        Long hospitalId = requireHospitalId();
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1");
        if (SOURCE_MEDICINE.equals(source)) return takeMedicine(admission, hospitalId, medicineId, quantity, marId);
        if (SOURCE_HOSPITAL_ITEM.equals(source)) return takeItem(admission, hospitalId, itemName, quantity);
        throw new IllegalArgumentException("This order is not linked to the hospital's stock");
    }

    private Taken takeMedicine(IpdAdmission admission, Long hospitalId, Long medicineId, int quantity, Long marId) {
        Medicine m = medicineRepository.findByIdAndHospitalId(medicineId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("That medicine is no longer in the inventory"));
        int available = medicineStock.availableQuantityFor(medicineId, hospitalId);
        if (available < quantity) throw notEnough(m.getName(), available);
        medicineStock.consumeFefo(medicineId, quantity, MOVEMENT_TYPE, REFERENCE_TYPE, marId, key(marId),
                "Given to IPD " + admission.getIpdNumber());
        BigDecimal unit = m.getUnitPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(m.getUnitPrice());
        BigDecimal charge = unit.multiply(BigDecimal.valueOf(quantity));
        Long billingMedicineId = null;
        Billing bill = ipdBill(admission, hospitalId);
        if (bill != null && charge.signum() > 0) {
            BillingMedicine bm = new BillingMedicine();
            bm.setBillingId(bill.getId());
            bm.setHospitalId(hospitalId);
            bm.setMedicineId(m.getId());
            bm.setMedicineName(m.getName());
            bm.setQuantity(quantity);
            bm.setUnitPrice(unit);
            bm.setAmount(charge);
            billingMedicineId = billingMedicineRepository.save(bm).getId();
            billingService.recalculateTotal(bill.getId());
        }
        return new Taken(bill != null ? charge : BigDecimal.ZERO, null, billingMedicineId, null);
    }

    private Taken takeItem(IpdAdmission admission, Long hospitalId, String itemName, int quantity) {
        if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("Choose the item to give");
        List<HospitalInventory> rows = new ArrayList<>(inventoryRepository.lockActiveByName(itemName, hospitalId));
        int available = rows.stream().mapToInt(r -> r.getStockQuantity() == null ? 0 : r.getStockQuantity()).sum();
        if (available < quantity) throw notEnough(itemName, available);
        // Earliest expiry first; rows without an expiry last.
        rows.sort(Comparator.comparing(HospitalInventory::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));
        int remaining = quantity;
        BigDecimal charge = BigDecimal.ZERO;
        StringBuilder detail = new StringBuilder();
        for (HospitalInventory r : rows) {
            int have = r.getStockQuantity() == null ? 0 : r.getStockQuantity();
            int take = Math.min(have, remaining);
            if (take <= 0) continue;
            r.setStockQuantity(have - take);
            inventoryRepository.save(r);
            if (r.getUnitPrice() != null) charge = charge.add(BigDecimal.valueOf(r.getUnitPrice()).multiply(BigDecimal.valueOf(take)));
            if (!detail.isEmpty()) detail.append(',');
            detail.append(r.getId()).append(':').append(take);
            remaining -= take;
            if (remaining == 0) break;
        }
        Long billingItemId = null;
        Billing bill = ipdBill(admission, hospitalId);
        if (bill != null && charge.signum() > 0) {
            BillingItem bi = new BillingItem();
            bi.setBillingId(bill.getId());
            bi.setHospitalId(hospitalId);
            bi.setDescription(itemName + " × " + quantity);
            bi.setAmount(charge);
            billingItemId = billingItemRepository.save(bi).getId();
            billingService.recalculateTotal(bill.getId());
        }
        return new Taken(bill != null ? charge : BigDecimal.ZERO, billingItemId, null, detail.toString());
    }

    /** Undoes a dose's stock and charge. */
    @Transactional
    public void putBack(MedicationAdministration mar) {
        Long hospitalId = requireHospitalId();
        if (SOURCE_MEDICINE.equals(mar.getStockSource())) {
            medicineStock.returnConsumption(key(mar.getId()), "Dose undone");
        } else if (SOURCE_HOSPITAL_ITEM.equals(mar.getStockSource()) && mar.getStockDetail() != null) {
            for (String part : mar.getStockDetail().split(",")) {
                String[] idQty = part.split(":");
                if (idQty.length != 2) continue;
                inventoryRepository.findByIdAndHospitalId(Long.valueOf(idQty[0]), hospitalId).ifPresent(r -> {
                    r.setStockQuantity((r.getStockQuantity() == null ? 0 : r.getStockQuantity()) + Integer.parseInt(idQty[1]));
                    inventoryRepository.save(r);
                });
            }
        }
        Long billId = null;
        if (mar.getBillingMedicineId() != null) {
            BillingMedicine bm = billingMedicineRepository.findById(mar.getBillingMedicineId()).orElse(null);
            if (bm != null && hospitalId.equals(bm.getHospitalId())) {
                billId = bm.getBillingId();
                billingMedicineRepository.delete(bm);
            }
        }
        if (mar.getBillingItemId() != null) {
            BillingItem bi = billingItemRepository.findById(mar.getBillingItemId()).orElse(null);
            if (bi != null && hospitalId.equals(bi.getHospitalId())) {
                billId = bi.getBillingId();
                billingItemRepository.delete(bi);
            }
        }
        if (billId != null) billingService.recalculateTotal(billId);
    }

    /** Whether a dose's charge can still be taken off its bill (not once the bill is settled). */
    public void assertChargeRemovable(MedicationAdministration mar) {
        Long billingId = null;
        if (mar.getBillingMedicineId() != null) {
            billingId = billingMedicineRepository.findById(mar.getBillingMedicineId()).map(BillingMedicine::getBillingId).orElse(null);
        } else if (mar.getBillingItemId() != null) {
            billingId = billingItemRepository.findById(mar.getBillingItemId()).map(BillingItem::getBillingId).orElse(null);
        }
        if (billingId == null) return;
        Long hospitalId = requireHospitalId();
        billingRepository.findById(billingId).filter(b -> hospitalId.equals(b.getHospitalId())).ifPresent(b -> {
            String st = b.getPaymentStatus();
            if ("CLOSED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st)) {
                throw new IllegalArgumentException("The bill this dose was charged to is already closed, so it can't be undone.");
            }
        });
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static String key(Long marId) {
        return "MAR-" + marId;
    }

    private static ConflictException notEnough(String name, int available) {
        return new ConflictException(available <= 0
                ? name + " is out of stock. Tick \"Patient's own supply\" if the patient brought it, and ask for it to be reordered."
                : "Only " + available + " of " + name + " left. Give " + available + " or less, or tick \"Patient's own supply\".");
    }

    /** The admission's IPD bill when the hospital bills; null otherwise (stock is still taken). */
    private Billing ipdBill(IpdAdmission admission, Long hospitalId) {
        boolean billing = hospitalRepository.findById(hospitalId)
                .map(h -> h.getModules() != null && h.getModules().contains("BILLING")).orElse(false);
        if (!billing) return null;
        return billingRepository.findByIpdAdmissionId(admission.getId()).stream()
                .filter(b -> hospitalId.equals(b.getHospitalId()))
                .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getPaymentStatus()) && !"CLOSED".equalsIgnoreCase(b.getPaymentStatus()))
                .findFirst().orElse(null);
    }

    private Map<String, List<HospitalInventory>> itemsByName(Long hospitalId) {
        Map<String, List<HospitalInventory>> byName = new LinkedHashMap<>();
        for (HospitalInventory r : inventoryRepository.findByHospitalIdAndIsActiveTrue(hospitalId)) {
            if (r.getName() == null) continue;
            byName.computeIfAbsent(r.getName(), k -> new ArrayList<>()).add(r);
        }
        return byName;
    }

    private static Map<String, Object> itemOption(String name, List<HospitalInventory> rows) {
        int available = rows.stream().mapToInt(r -> r.getStockQuantity() == null ? 0 : r.getStockQuantity()).sum();
        int min = rows.stream().map(HospitalInventory::getMinStockLevel).filter(java.util.Objects::nonNull)
                .max(Integer::compare).orElse(0);
        BigDecimal price = rows.stream().map(HospitalInventory::getUnitPrice).filter(java.util.Objects::nonNull)
                .findFirst().map(BigDecimal::valueOf).orElse(null);
        LocalDate expiry = rows.stream().filter(r -> r.getStockQuantity() != null && r.getStockQuantity() > 0)
                .map(HospitalInventory::getExpiryDate).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        String type = rows.stream().map(HospitalInventory::getType).filter(java.util.Objects::nonNull).findFirst().orElse("Item");
        return option(SOURCE_HOSPITAL_ITEM, null, name, type, available, min, price, expiry);
    }

    private static Map<String, Object> option(String source, Long medicineId, String name, String type, int available,
                                              int minLevel, BigDecimal unitPrice, LocalDate expiry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", source);
        m.put("medicineId", medicineId);
        m.put("itemName", SOURCE_HOSPITAL_ITEM.equals(source) ? name : null);
        m.put("name", name);
        m.put("type", type);
        m.put("available", available);
        m.put("minLevel", minLevel);
        m.put("low", available <= minLevel);
        m.put("outOfStock", available <= 0);
        m.put("unitPrice", unitPrice);
        m.put("earliestExpiry", expiry);
        m.put("inventory", SOURCE_MEDICINE.equals(source) ? "Medicine inventory" : "Hospital inventory");
        return m;
    }

    private Long requireHospitalId() {
        Long id = securityHelper.getCurrentHospitalId();
        if (id == null) throw new com.hms.exception.UnauthorizedException("Hospital ID not found in context");
        return id;
    }
}
