package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HospitalSetting - Entity representing the operational configurations for a hospital
 * 
 * Maps to 'hospital_settings' table. Handles toggles like receptionist mode
 * and billing ownership.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "hospital_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false, unique = true)
    private Hospital hospital;

    @Column(name = "reception_mode", nullable = false, length = 20)
    private String receptionMode = "HAS_RECEPTIONIST"; // HAS_RECEPTIONIST or SOLO

    @Column(name = "billing_handler", nullable = false, length = 20)
    private String billingHandler = "RECEPTIONIST"; // RECEPTIONIST or DOCTOR

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Hospital getHospital() {
        return hospital;
    }

    public void setHospital(Hospital hospital) {
        this.hospital = hospital;
    }

    public String getReceptionMode() {
        return receptionMode;
    }

    public void setReceptionMode(String receptionMode) {
        this.receptionMode = receptionMode;
    }

    public String getBillingHandler() {
        return billingHandler;
    }

    public void setBillingHandler(String billingHandler) {
        this.billingHandler = billingHandler;
    }
}
