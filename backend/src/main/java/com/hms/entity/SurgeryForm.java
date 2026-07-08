package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * SurgeryForm - a saved OT/NABH form for a surgery patient (OT module).
 * Generic JSON store: each form type (e.g. BLOOD_CONSENT, WHO_CHECKLIST) keeps
 * its field values as JSON in {@code dataJson}, keyed by IPD admission + form
 * type (one saved instance per form per patient). The nurse fills it, saves it,
 * then prints it. Field layouts live on the frontend.
 */
@Entity
@Table(name = "surgery_forms",
        uniqueConstraints = @UniqueConstraint(name = "UK_surgery_form_admission_type",
                columnNames = {"ipd_admission_id", "form_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SurgeryForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "surgery_id")
    private Long surgeryId;

    @Column(name = "form_type", nullable = false, length = 60)
    private String formType;

    @Column(name = "data_json", columnDefinition = "longtext")
    private String dataJson;

    @Column(name = "saved_by_user_id")
    private Long savedByUserId;

    @Column(name = "performed_by_nurse_id")
    private Long performedByNurseId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }
}
