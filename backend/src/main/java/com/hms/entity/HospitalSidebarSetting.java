package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Which sidebar tabs one role does not see in one hospital (Settings → Sidebar). Display only:
 * it never grants or removes a permission. No row for a role means that role's defaults.
 */
@Entity
@Table(name = "hospital_sidebar_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hospital_id", "role"}))
@Data
@NoArgsConstructor
public class HospitalSidebarSetting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;
    @Column(name = "role", nullable = false, length = 30)
    private String role;
    /** Comma-separated tab ids hidden for this role. */
    @Column(name = "hidden_tabs", nullable = false, length = 1000)
    private String hiddenTabs = "";
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
