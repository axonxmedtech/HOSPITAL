package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for hospital inventory item create/update requests (active stock).
 * Excludes server-side fields (id, hospitalId, isActive, createdAt) to
 * eliminate the S4684 mass-assignment risk.
 */
public class InventoryItemRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name is too long")
    private String name;

    private Integer stockQuantity;
    private Double unitPrice;
    private LocalDate expiryDate;
    private Integer minStockLevel;

    @Size(max = 50, message = "Type is too long")
    private String type;

    @Size(max = 100, message = "Manufacturer is too long")
    private String manufacturer;

    // Getters and setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

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

    /** Map to a fresh HospitalInventory entity. */
    public com.hms.entity.HospitalInventory toEntity() {
        com.hms.entity.HospitalInventory h = new com.hms.entity.HospitalInventory();
        h.setName(name);
        h.setStockQuantity(stockQuantity);
        h.setUnitPrice(unitPrice);
        h.setExpiryDate(expiryDate);
        h.setMinStockLevel(minStockLevel);
        h.setType(type);
        h.setManufacturer(manufacturer);
        return h;
    }
}

