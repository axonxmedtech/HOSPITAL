package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipment_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentAssignment extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(length = 30)
    private String status = "ASSIGNED";
}
