package com.hms.dto;

import lombok.Data;

import java.util.Map;

/** Nurse's save payload for an OT/NABH surgery form. */
@Data
public class SaveSurgeryFormRequest {
    private Long ipdAdmissionId;         // required
    private String formType;             // required, e.g. "BLOOD_CONSENT"
    private Map<String, Object> data;    // arbitrary field values for that form
    private Long performedByNurseId;     // optional; required when Separate Nurse Login is OFF
}
