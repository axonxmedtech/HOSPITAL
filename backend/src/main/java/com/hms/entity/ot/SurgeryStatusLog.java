package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "surgery_status_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SurgeryStatusLog extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    @Column(nullable = false, length = 60)
    private String status;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime = LocalDateTime.now();

    @Column(length = 1000)
    private String notes;
}
