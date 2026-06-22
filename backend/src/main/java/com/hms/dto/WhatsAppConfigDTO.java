package com.hms.dto;

public class WhatsAppConfigDTO {
    private String accessToken;
    private String phoneNumberId;
    private String wabaId;
    private Boolean active;
    private Boolean sendAppointments;
    private Boolean sendBilling;
    private Boolean sendCasePapers;
    private Boolean sendPrescription;
    private Boolean sendMedicineList;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Boolean getSendAppointments() { return sendAppointments; }
    public void setSendAppointments(Boolean sendAppointments) { this.sendAppointments = sendAppointments; }
    public Boolean getSendBilling() { return sendBilling; }
    public void setSendBilling(Boolean sendBilling) { this.sendBilling = sendBilling; }
    public Boolean getSendCasePapers() { return sendCasePapers; }
    public void setSendCasePapers(Boolean sendCasePapers) { this.sendCasePapers = sendCasePapers; }
    public Boolean getSendPrescription() { return sendPrescription; }
    public void setSendPrescription(Boolean sendPrescription) { this.sendPrescription = sendPrescription; }
    public Boolean getSendMedicineList() { return sendMedicineList; }
    public void setSendMedicineList(Boolean sendMedicineList) { this.sendMedicineList = sendMedicineList; }
}
