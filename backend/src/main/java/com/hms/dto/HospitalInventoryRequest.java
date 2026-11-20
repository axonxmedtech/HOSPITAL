package com.hms.dto;

import com.hms.entity.HospitalInventory;
import com.hms.validation.NoEmoji;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * What a client may send to add or edit a hospital-inventory item. The id and hospital are never
 * taken from the request: adding an item with someone else's id could otherwise overwrite that
 * row. Validation matches the entity, so error messages are unchanged.
 */
@Data
public class HospitalInventoryRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name is too long")
    @NoEmoji
    private String name;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Double unitPrice;
    private LocalDate expiryDate;

    @Min(value = 0, message = "Minimum stock level cannot be negative")
    private Integer minStockLevel;

    @Size(max = 50, message = "Type is too long")
    @NoEmoji
    private String type;

    @Size(max = 100, message = "Manufacturer is too long")
    @NoEmoji
    private String manufacturer;

    private Boolean isActive;

    /** A new, unsaved item holding only the client-supplied fields (active unless said otherwise). */
    public HospitalInventory toEntity() {
        HospitalInventory i = new HospitalInventory();
        i.setName(name);
        i.setStockQuantity(stockQuantity);
        i.setUnitPrice(unitPrice);
        i.setExpiryDate(expiryDate);
        i.setMinStockLevel(minStockLevel);
        i.setType(type);
        i.setManufacturer(manufacturer);
        if (isActive != null) i.setIsActive(isActive);
        return i;
    }
}
