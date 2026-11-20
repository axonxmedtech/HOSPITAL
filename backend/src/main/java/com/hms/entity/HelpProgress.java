package com.hms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One user's in-app help progress: guide steps ticked, tours finished, the welcome tour offer
 * answered. Stored as a small JSON document the frontend owns, so it follows the user to any
 * computer. Preference data only — no clinical or financial content.
 */
@Entity
@Table(name = "help_progress")
@Data
@NoArgsConstructor
public class HelpProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user's facility; null for the Super Admin. */
    @Column(name = "hospital_id")
    private Long hospitalId;

    /** Who the row belongs to: role, facility, branch and user id. */
    @Column(name = "scope_key", nullable = false, unique = true, length = 160)
    private String scopeKey;

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
