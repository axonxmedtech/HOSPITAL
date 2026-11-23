package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ipd_admission", uniqueConstraints = @UniqueConstraint(
        name = "uk_ipd_admission_hospital_ipd_number", columnNames = {"hospital_id", "ipd_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpdAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IPD-1, IPD-2 ... numbered per hospital (unique with hospital_id). */
    @Column(name = "ipd_number", nullable = false)
    private String ipdNumber;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "source_opd_id")
    private Long sourceOpdId;

    @Column(name = "admission_type", nullable = false)
    private String admissionType; // EMERGENCY / ELECTIVE

    @Column(name = "status", nullable = false)
    private String status; // ADMITTED / DISCHARGED

    @Column(name = "admission_datetime", nullable = false)
    private LocalDateTime admissionDatetime;

    @Column(name = "discharge_datetime")
    private LocalDateTime dischargeDatetime;

    /**
     * How the patient left: NORMAL (discharged home), LAMA (left against medical advice),
     * REFERRED (transferred to another hospital), EXPIRED (death) or ABSCONDED. Set at discharge.
     */
    @Column(name = "discharge_type", length = 20)
    private String dischargeType;

    /** Referral hospital, cause / time of death, or any other note about the discharge. */
    @Column(name = "discharge_note", length = 500)
    private String dischargeNote;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "primary_diagnosis", columnDefinition = "text")
    private String primaryDiagnosis;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /**
     * Whether the nurse has completed the admission form and confirmed the
     * patient as admitted (after collecting the signed printed form). Until then
     * the admission is "pending" even though a bed is reserved.
     */
    @Column(name = "admission_confirmed", nullable = false)
    private Boolean admissionConfirmed = false;

    @Column(name = "admission_confirmed_at")
    private LocalDateTime admissionConfirmedAt;

    /** users.id of the receptionist/admin who admitted the patient (assigned ward/bed). */
    @Column(name = "admitted_by_user_id")
    private Long admittedByUserId;

    /** Why an admission made by mistake was cancelled (status CANCELLED). */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Role and email of whoever cancelled the admission. */
    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    /** CALENDAR_DAY or HOURS_24: the hospital's bed-charge rule when the patient was admitted. Null = CALENDAR_DAY. */
    @Column(name = "bed_charge_rule", length = 20)
    private String bedChargeRule;

    /** Grace hours of the HOURS_24 rule, as it was at admission. */
    @Column(name = "grace_hours")
    private Integer graceHours;

    /** The fixed-price package covering this stay, if any. */
    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_applied_at")
    private LocalDateTime packageAppliedAt;
}
