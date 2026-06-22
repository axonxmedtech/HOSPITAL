package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "anesthesia_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnesthesiaRecord extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "anesthesia_type", nullable = false, length = 40)
    private String anesthesiaType;

    @Column(name = "drug_chart", length = 4000)
    private String drugChart;

    @Column(length = 40)
    private String bp;

    private Integer pulse;
    private Integer spo2;
    private Double temperature;
    private Integer respiration;

    @Column(length = 2000)
    private String complications;
}
