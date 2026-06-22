package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "instrument_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentAssignment extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "instrument_set_id", nullable = false)
    private Long instrumentSetId;

    @Column(length = 30)
    private String status = "ASSIGNED";
}
