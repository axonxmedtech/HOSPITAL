package com.hms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ConsultationRequest {
    private Long appointmentId; // Optional - for appointment-based consultations
    private Long opdId; // Optional - for OPD-based consultations
    private String patientId; // Required - patient public ID or numeric id
    private String symptoms;
    private String diagnosis;
    private String treatmentNotes;
    private LocalDate followUpDate;
    private List<PrescriptionItem> prescription;
    private List<String> labTests;
    private List<AdministeredItem> administeredItems;

    @Data
    public static class PrescriptionItem {
        private String medicineName;
        private String dosage;
        private String frequency;
        private String duration;
        private String instructions;
    }

    @Data
    public static class AdministeredItem {
        private Long medicineId;
        private String medicineName;
        private Integer quantity;
    }

    private List<ChargeItem> charges;

    @Data
    public static class ChargeItem {
        private String description;
        private java.math.BigDecimal amount;
    }
}
