package com.hms.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One hospital's counter for one kind of readable number (PATIENT → PAT…, BILLING → BIL…).
 * Written only by {@code TenantCustomIdAllocator} under the hospital's row lock; it holds the next
 * number to hand out, so each hospital counts 1, 2, 3 on its own. No clinical content.
 */
@Entity
@Table(name = "tenant_number_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_number_sequences",
                columnNames = {"hospital_id", "kind"}))
public class TenantNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getHospitalId() { return hospitalId; }
    public String getKind() { return kind; }
    public Long getNextValue() { return nextValue; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
