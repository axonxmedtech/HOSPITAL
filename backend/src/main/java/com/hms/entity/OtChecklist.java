package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ot_checklists")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false, unique = true)
    private Long otBookingId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "sign_in_completed", nullable = false)
    private boolean signInCompleted = false;

    @Column(name = "sign_in_by", length = 100)
    private String signInBy;

    @Column(name = "sign_in_at")
    private LocalDateTime signInAt;

    @Column(name = "sign_in_notes", columnDefinition = "TEXT")
    private String signInNotes;

    @Column(name = "time_out_completed", nullable = false)
    private boolean timeOutCompleted = false;

    @Column(name = "time_out_by", length = 100)
    private String timeOutBy;

    @Column(name = "time_out_at")
    private LocalDateTime timeOutAt;

    @Column(name = "time_out_notes", columnDefinition = "TEXT")
    private String timeOutNotes;

    @Column(name = "sign_out_completed", nullable = false)
    private boolean signOutCompleted = false;

    @Column(name = "sign_out_by", length = 100)
    private String signOutBy;

    @Column(name = "sign_out_at")
    private LocalDateTime signOutAt;

    @Column(name = "sign_out_notes", columnDefinition = "TEXT")
    private String signOutNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
