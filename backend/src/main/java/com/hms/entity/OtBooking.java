package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ot_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "procedure_name", nullable = false, length = 200)
    private String procedureName;

    @Column(name = "scheduled_date_time", nullable = false)
    private LocalDateTime scheduledDateTime;

    @Column(name = "surgeon_id", nullable = false)
    private Long surgeonId;

    @Column(name = "anesthetist_name", length = 100)
    private String anesthetistName;

    @Column(name = "ot_room_number", nullable = false, length = 50)
    private String otRoomNumber;

    // SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
