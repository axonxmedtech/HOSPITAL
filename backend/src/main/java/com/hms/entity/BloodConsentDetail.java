package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BloodConsentDetail - Entity containing blood transfusion-specific consent fields,
 * linked 1:1 to PatientConsent.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "blood_consent_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodConsentDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consent_id", nullable = false, unique = true)
    private Long consentId;

    @Column(name = "explanation_given", nullable = false)
    private Boolean explanationGiven = false;

    @Column(name = "witness_patient_name", length = 100)
    private String witnessPatientName;

    @Column(name = "witness_patient_signed", nullable = false)
    private Boolean witnessPatientSigned = false;

    @Column(name = "witness_hospital_name", length = 100)
    private String witnessHospitalName;

    @Column(name = "witness_hospital_signed", nullable = false)
    private Boolean witnessHospitalSigned = false;

    @Column(name = "interpreter_required", nullable = false)
    private Boolean interpreterRequired = false;

    @Column(name = "interpreter_language", length = 40)
    private String interpreterLanguage;

    @Column(name = "interpreter_name", length = 100)
    private String interpreterName;

    @Column(name = "interpreter_signed", nullable = false)
    private Boolean interpreterSigned = false;

    @Column(name = "blood_request_id")
    private Long bloodRequestId;
}
