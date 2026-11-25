package com.hms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One act of writing a clinical order (an IPD prescription, a set of lab tests), keyed by the
 * client's idempotency key. A retry of the same act -- a double-click, or a resend after a lost
 * response -- finds this row and is answered with the records it already created, instead of
 * ordering the medicine or the test twice. {@code scope} names the kind of act, so the same key
 * can never replay a different kind of order.
 */
@Entity
@Table(name = "request_idempotency",
        uniqueConstraints = @UniqueConstraint(name = "uk_request_idempotency",
                columnNames = {"hospital_id", "scope", "idempotency_key"}))
public class RequestIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "scope", nullable = false, length = 40)
    private String scope;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** Comma-separated ids of the records the act created, in order. */
    @Column(name = "result_ids", nullable = false, length = 500)
    private String resultIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getResultIds() { return resultIds; }
    public void setResultIds(String resultIds) { this.resultIds = resultIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
