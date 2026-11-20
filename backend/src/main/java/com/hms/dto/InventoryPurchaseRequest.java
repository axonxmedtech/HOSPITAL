package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * DTO for recording a new hospital inventory purchase.
 * Excludes server-side fields (id, hospitalId, purchaseDate) to eliminate
 * the S4684 mass-assignment risk.
 */
public class InventoryPurchaseRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private Integer quantity;
    private Double unitPrice;
    private LocalDate expiryDate;
    private Integer minStockLevel;
    private String type;
    private String manufacturer;

    // Getters and setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public Integer getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(Integer minStockLevel) { this.minStockLevel = minStockLevel; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    /** Map to a fresh HospitalInventoryPurchase entity. */
    public com.hms.entity.HospitalInventoryPurchase toEntity() {
        com.hms.entity.HospitalInventoryPurchase p = new com.hms.entity.HospitalInventoryPurchase();
        p.setName(name);
        p.setQuantity(quantity);
        p.setUnitPrice(unitPrice);
        p.setExpiryDate(expiryDate);
        p.setMinStockLevel(minStockLevel != null ? minStockLevel : 10);
        p.setType(type);
        p.setManufacturer(manufacturer);
        return p;
    }
}

