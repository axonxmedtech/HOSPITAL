package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ot_consumable_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtConsumableUsage extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(nullable = false, length = 140)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_charge", precision = 10, scale = 2)
    private BigDecimal unitCharge = BigDecimal.ZERO;
}
