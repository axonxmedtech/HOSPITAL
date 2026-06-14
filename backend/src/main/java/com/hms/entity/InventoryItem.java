package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * InventoryItem - Entity representing the catalog list of non-medicine hospital inventory items
 * 
 * Maps to 'inventory_items' table.
 */
@Entity
@Table(name = "inventory_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String type; // e.g. Syringe, Fluid, Consumable, Surgical, Gloves

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * The ID of the linked HospitalFee row.
     * When this catalog item is used in a consultation/IPD, the fee identified
     * by this ID is auto-applied to the patient's bill.
     */
    @Column(name = "linked_fee_id")
    private Long linkedFeeId;

    @Column(name = "relative_item_ids", length = 1000)
    private String relativeItemIds; // JSON array of numbers, e.g. "[12, 15]"
}
