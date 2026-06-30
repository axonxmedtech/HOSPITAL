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

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hospital_id", nullable = false, unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Hospital hospital;

    @Column(name = "reception_mode", nullable = false, length = 20)
    private String receptionMode = "HAS_RECEPTIONIST"; // HAS_RECEPTIONIST or SOLO

    @Column(name = "billing_handler", nullable = false, length = 20)
    private String billingHandler = "RECEPTIONIST"; // RECEPTIONIST or DOCTOR

    @Column(name = "in_clinic", nullable = false)
    private Boolean inClinic = true;

    @Column(name = "shift_mode", nullable = false, length = 20)
    private String shiftMode = "FIXED";

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

    public Boolean getInClinic() {
        return inClinic;
    }

    public void setInClinic(Boolean inClinic) {
        this.inClinic = inClinic;
    }

    public String getShiftMode() {
        return shiftMode;
    }

    public void setShiftMode(String shiftMode) {
        this.shiftMode = shiftMode;
    }
}
