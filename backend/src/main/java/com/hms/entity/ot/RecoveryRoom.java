package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recovery_room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryRoom extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(length = 40)
    private String bp;

    private Integer pulse;
    private Integer spo2;
    private Integer painScore;

    @Column(length = 80)
    private String consciousness;

    private Boolean nausea = false;

    @Column(length = 80)
    private String drainOutput;

    @Column(length = 80)
    private String urineOutput;

    @Column(length = 40)
    private String disposition;

    @Column(length = 1000)
    private String notes;
}
