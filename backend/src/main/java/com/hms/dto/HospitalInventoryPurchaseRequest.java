package com.hms.dto;

import com.hms.entity.HospitalInventoryPurchase;
import lombok.Data;

import java.time.LocalDate;

/** What a client may send to record a hospital-inventory purchase; id and hospital are server-set. */
@Data
public class HospitalInventoryPurchaseRequest {
    private String name;
    private Integer quantity;
    private Double unitPrice;
    private LocalDate expiryDate;
    private Integer minStockLevel;
    private String type;
    private String manufacturer;

    public HospitalInventoryPurchase toEntity() {
        HospitalInventoryPurchase p = new HospitalInventoryPurchase();
        p.setName(name);
        p.setQuantity(quantity);
        p.setUnitPrice(unitPrice);
        p.setExpiryDate(expiryDate);
        p.setMinStockLevel(minStockLevel);
        p.setType(type);
        p.setManufacturer(manufacturer);
        return p;
    }
}
