package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "implant_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImplantUsage extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(nullable = false, length = 140)
    private String implantName;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String lotNumber;

    @Column(length = 100)
    private String batchNumber;

    @Column(length = 100)
    private String serialNumber;

    @Column(length = 140)
    private String manufacturer;

    private LocalDate expiryDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal charge = BigDecimal.ZERO;
}
