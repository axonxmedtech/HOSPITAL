package com.hms.dto;

import lombok.Data;

import java.util.Map;

/** Nurse's save payload for an OT/NABH surgery form. */
@Data
public class SaveSurgeryFormRequest {
    /**
     * The procedure this form belongs to. Preferred. When absent the surgery is
     * resolved from ipdAdmissionId, which is ambiguous once an admission carries
     * more than one procedure, so callers that know the surgery should send it.
     */
    private Long surgeryId;
    private Long ipdAdmissionId;         // legacy / fallback resolution
    private String formType;             // required, e.g. "BLOOD_CONSENT"
    private Map<String, Object> data;    // arbitrary field values for that form
    private Long performedByNurseId;     // optional; required when Separate Nurse Login is OFF
    /** Sign on save. A signed form is immutable; a later edit creates a new version. */
    private Boolean sign;
}
