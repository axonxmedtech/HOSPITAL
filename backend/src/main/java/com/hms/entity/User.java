package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * User - Entity representing all users in the system
 * 
 * This entity stores information for ALL users including:
 * - Super Admin (hospital_id = NULL, role = SUPER_ADMIN)
 * - Hospital Admin (hospital_id = valid ID, role = HOSPITAL_ADMIN)
 * - Doctor (hospital_id = valid ID, role = DOCTOR)
 * 
 * The hospital_id field is critical for multi-tenant isolation.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Unique identifier for the user
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public unique identifier (UUID) for security
     */
    /**
     * Public unique identifier (UUID) for security
     */
    @Column(nullable = false, unique = true)
    private String publicId;

    /**
     * Custom readable ID for UI display (e.g., USR1234)
     */
    @Column(name = "custom_id")
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
        // customId for RECEPTIONIST is set by ReceptionistService after save (sequential)
        // Other roles retain random generation for now
        if (this.customId == null && !"RECEPTIONIST".equals(this.role)) {
            String prefix = "USR";
            if ("HOSPITAL_ADMIN".equals(this.role))
                prefix = "ADM";
            else if ("DOCTOR".equals(this.role))
                prefix = "DOC";
            else if ("SUPER_ADMIN".equals(this.role))
                prefix = "SUP";
            this.customId = prefix + (1000 + new java.util.Random().nextInt(9000));
        }
    }

    /**
     * Email address used for login
     * Must be unique across the entire system
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Encrypted password
     */
    @Column(nullable = false)
    private String password;

    /**
     * User's full name
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Role of the user: SUPER_ADMIN, HOSPITAL_ADMIN, or DOCTOR
     */
    @Column(nullable = false, length = 20)
    private String role;

    /**
     * Hospital ID for multi-tenant isolation
     * - NULL for Super Admin users
     * - Valid hospital ID for Hospital Admin and Doctor users
     * 
     * This field is used to filter all hospital-related data
     */
    @Column(name = "hospital_id")
    private Long hospitalId;

    /**
     * Soft delete flag
     */
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * Timestamp when the user was created
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(Long hospitalId) {
        this.hospitalId = hospitalId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
