package com.hms.event;

public class MedicineDispensedEvent {
    private final Long hospitalId;
    private final Long patientId;   // nullable — not all purchases are patient-linked
    private final Long purchaseId;

    public MedicineDispensedEvent(Long hospitalId, Long patientId, Long purchaseId) {
        this.hospitalId = hospitalId;
        this.patientId = patientId;
        this.purchaseId = purchaseId;
    }

    public Long getHospitalId() { return hospitalId; }
    public Long getPatientId() { return patientId; }
    public Long getPurchaseId() { return purchaseId; }
}
