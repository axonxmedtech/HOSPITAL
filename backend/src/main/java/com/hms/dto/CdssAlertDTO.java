package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CdssAlertDTO {
    private String type;        // ALLERGY | DUPLICATE_MEDICINE | DRUG_INTERACTION | CRITICAL_LAB | EWS_HIGH | EWS_MEDIUM
    private String severity;    // HIGH | MEDIUM
    private String title;
    private String message;
    private String suggestion;
}
