package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "surgery")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Surgery extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 100)
    private String specialty;

    @Column(name = "procedure_code", length = 50)
    private String procedureCode;

    @Column(name = "default_duration_minutes")
    private Integer defaultDurationMinutes = 60;

    @Column(name = "default_charge", precision = 10, scale = 2)
    private BigDecimal defaultCharge = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean active = true;
}
