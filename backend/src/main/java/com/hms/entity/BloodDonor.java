package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Blood Donor (Form 38 core) — donor demographics and eligibility. */
@Entity
@Table(name = "blood_donor")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodDonor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "donor_number", nullable = false, unique = true, length = 20)
    private String donorNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(name = "rh_type", nullable = false, length = 10)
    private String rhType; // POSITIVE / NEGATIVE

    // ELIGIBLE / DEFERRED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "deferral_expiry")
    private LocalDate deferralExpiry;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
