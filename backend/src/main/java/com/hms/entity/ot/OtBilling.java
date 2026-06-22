package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ot_billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtBilling extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "billing_id")
    private Long billingId;

    @Column(nullable = false, length = 120)
    private String chargeType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;
}
