package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Hospital - Entity representing a hospital (tenant) in the multi-tenant system
 * 
 * This entity stores information about each hospital registered in the
 * platform.
 * Each hospital is a separate tenant with isolated data.
 * Only Super Admin can create and manage hospitals.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "hospitals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hospital {

    /**
     * Unique identifier for the hospital
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public unique identifier (UUID) for security
     */
    @Column(nullable = false, unique = true)
    private String publicId;

    /**
     * Custom readable ID for UI display (e.g., HSP1234)
     */
    @Column(name = "custom_id", unique = true)
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
        if (this.customId == null) {
            // Generate simple random readable ID: HSP + 4 random digits
            // Note: In production, check for uniqueness or use a sequence
            this.customId = "HSP" + (1000 + new java.util.Random().nextInt(9000));
        }
    }

    /**
     * Name of the hospital
     */
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "parent_organization", length = 200)
    private String parentOrganization;

    /**
     * Whether the hospital is currently active
     * Inactive hospitals cannot be accessed by their users
     */
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * Subscription plan for the hospital (FREE or PAID)
     * Manual management in Phase-1, no automated billing
     */
    @Column(nullable = false, length = 20)
    private String plan = "FREE";

    /**
     * CMS: Standard consultation fee for OPD
     */
    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private java.math.BigDecimal consultationFee;

    /**
     * CMS: Case paper fee for OPD (optional, fallback used when null)
     */
    @Column(name = "case_paper_fee", precision = 10, scale = 2)
    private java.math.BigDecimal casePaperFee;

    /**
     * CMS: OPD Timings (e.g., "Mon-Sat: 10AM - 6PM")
     */
    @Column(name = "opd_timings", length = 100)
    private String opdTimings;

    /**
     * Timestamp when the hospital was created
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Enabled modules for this hospital (CMS/HMS features).
     * Default: OPD, BILLING (CMS Mode)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hospital_modules", joinColumns = @JoinColumn(name = "hospital_id"))
    @Column(name = "module_name")
    private java.util.List<String> modules = new java.util.ArrayList<>(java.util.Arrays.asList("OPD", "BILLING"));

    /**
     * Whether this hospital is a single doctor hospital.
     */
    @Column(name = "is_single_doctor", nullable = false)
    private Boolean isSingleDoctor = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getCustomId() {
        return customId;
    }

    public void setCustomId(String customId) {
        this.customId = customId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public java.math.BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(java.math.BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public java.math.BigDecimal getCasePaperFee() {
        return casePaperFee;
    }

    public void setCasePaperFee(java.math.BigDecimal casePaperFee) {
        this.casePaperFee = casePaperFee;
    }

    public String getOpdTimings() {
        return opdTimings;
    }

    public void setOpdTimings(String opdTimings) {
        this.opdTimings = opdTimings;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.util.List<String> getModules() {
        return modules;
    }

    public void setModules(java.util.List<String> modules) {
        this.modules = modules;
    }

    public Boolean getIsSingleDoctor() {
        return isSingleDoctor;
    }

    public void setIsSingleDoctor(Boolean isSingleDoctor) {
        this.isSingleDoctor = isSingleDoctor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getParentOrganization() {
        return parentOrganization;
    }

    public void setParentOrganization(String parentOrganization) {
        this.parentOrganization = parentOrganization;
    }
}
