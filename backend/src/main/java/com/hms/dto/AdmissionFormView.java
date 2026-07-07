package com.hms.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hms.entity.AdmissionForm;
import lombok.Data;

/**
 * Response wrapper for the admission form: all form fields (unwrapped to the top
 * level via {@link JsonUnwrapped}, so the frontend still reads f.prnNo etc.) plus
 * live hospital branding for the printable header. A plain DTO is used instead of
 * {@code @Transient} entity fields because the registered Hibernate Jackson module
 * strips JPA {@code @Transient} properties from JSON.
 */
@Data
public class AdmissionFormView {

    @JsonUnwrapped
    private AdmissionForm form;

    private String hospitalName;
    private String hospitalLogoUrl;
    private String hospitalAddress;
    private String hospitalCustomId;

    public AdmissionFormView(AdmissionForm form, String hospitalName, String hospitalLogoUrl,
            String hospitalAddress, String hospitalCustomId) {
        this.form = form;
        this.hospitalName = hospitalName;
        this.hospitalLogoUrl = hospitalLogoUrl;
        this.hospitalAddress = hospitalAddress;
        this.hospitalCustomId = hospitalCustomId;
    }
}
