package com.hms.dto;

import com.hms.entity.*;
import lombok.Data;
import java.util.List;

/**
 * ClinicalAssessmentFinalizeRequest - Payload DTO to finalize initial clinical assessment,
 * spawning downstream orders and saving longitudinal EMR tables.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class ClinicalAssessmentFinalizeRequest {

    private List<PatientMedicalHistory> medicalHistory;

    private List<PatientSurgicalHistory> surgicalHistory;

    private List<PatientMedicationHistory> medicationHistory;

    private List<PatientFamilyHistory> familyHistory;

    private PatientSocialHistory socialHistory;

    private List<DoctorOrder> doctorOrders;
}
