package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CSSD sterile tray (Form 35 core) — a barcoded surgical instrument set that moves through
 * the DIRTY -> IN_STERILIZER -> STERILE -> ISSUED lifecycle. BR-1/BR-3: only STERILE, unexpired
 * trays may be issued. BR-2: a failed sterilization cycle quarantines every tray in the load.
 */
@Entity
@Table(name = "cssd_tray")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CssdTray {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "tray_name", nullable = false, length = 100)
    private String trayName;

    @Column(name = "specialty", length = 50)
    private String specialty;

    @Column(name = "barcode", nullable = false, unique = true, length = 30)
    private String barcode;

    // DIRTY / IN_STERILIZER / STERILE / ISSUED / QUARANTINED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
