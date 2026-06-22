package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "who_checklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhoChecklist extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false, unique = true)
    private Long otBookingId;

    private Boolean patientIdentity = false;
    private Boolean siteMarked = false;
    private Boolean consentSigned = false;
    private Boolean allergiesChecked = false;
    private Boolean bloodAvailable = false;
    private Boolean teamIntroduction = false;
    private Boolean antibioticGiven = false;
    private Boolean imagingDisplayed = false;
    private Boolean instrumentCount = false;
    private Boolean spongeCount = false;
    private Boolean finalCount = false;
    private Boolean specimenLabeled = false;
    private Boolean procedureConfirmed = false;
    private Boolean recoveryPlan = false;

    @Column(length = 30)
    private String status = "PENDING";
}
