package com.hms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ConsultationRequest {
    private Long appointmentId; // Optional - for appointment-based consultations
    private String patientId; // Required - patient public ID
    private String symptoms;
    private String diagnosis;
    private String treatmentNotes;
    private LocalDate followUpDate;
    private List<PrescriptionItem> prescription;

    @Data
    public static class PrescriptionItem {
        private String medicineName;
        private String dosage;
        private String frequency;
        private String duration;
        private String instructions;
    }
}
