package com.hms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_config")
public class WhatsAppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, unique = true)
    private Long hospitalId;

    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @Column(name = "phone_number_id", nullable = false, length = 100)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 100)
    private String wabaId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "send_appointments", nullable = false)
    private boolean sendAppointments = true;

    @Column(name = "send_billing", nullable = false)
    private boolean sendBilling = true;

    @Column(name = "send_case_papers", nullable = false)
    private boolean sendCasePapers = true;

    @Column(name = "send_prescription", nullable = false)
    private boolean sendPrescription = true;

    @Column(name = "send_medicine_list", nullable = false)
    private boolean sendMedicineList = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public boolean isSendAppointments() { return sendAppointments; }
    public void setSendAppointments(boolean sendAppointments) { this.sendAppointments = sendAppointments; }
    public boolean isSendBilling() { return sendBilling; }
    public void setSendBilling(boolean sendBilling) { this.sendBilling = sendBilling; }
    public boolean isSendCasePapers() { return sendCasePapers; }
    public void setSendCasePapers(boolean sendCasePapers) { this.sendCasePapers = sendCasePapers; }
    public boolean isSendPrescription() { return sendPrescription; }
    public void setSendPrescription(boolean sendPrescription) { this.sendPrescription = sendPrescription; }
    public boolean isSendMedicineList() { return sendMedicineList; }
    public void setSendMedicineList(boolean sendMedicineList) { this.sendMedicineList = sendMedicineList; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
