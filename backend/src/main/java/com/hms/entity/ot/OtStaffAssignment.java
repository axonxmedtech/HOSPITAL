package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ot_staff_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtStaffAssignment extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(name = "staff_user_id")
    private Long staffUserId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "staff_name", nullable = false, length = 120)
    private String staffName;

    @Column(nullable = false, length = 60)
    private String role;
}
