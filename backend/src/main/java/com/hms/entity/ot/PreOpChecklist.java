package com.hms.entity.ot;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pre_op_checklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreOpChecklist extends OtAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ot_booking_id", nullable = false, unique = true)
    private Long otBookingId;

    private Boolean consentSigned = false;
    private Boolean bloodAvailable = false;
    private Boolean cbc = false;
    private Boolean lft = false;
    private Boolean kft = false;
    private Boolean ptInr = false;
    private Boolean ecg = false;
    private Boolean chestXray = false;
    private Boolean crossMatching = false;
    private Boolean physicianFitness = false;
    private Boolean pacClearance = false;

    @Column(length = 30)
    private String status = "PENDING_CLEARANCE";

    @Column(length = 1000)
    private String notes;
}
