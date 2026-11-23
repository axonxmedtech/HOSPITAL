package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** How a hospital charges inpatients. No row = the defaults (calendar-day beds, nothing else). */
@Entity
@Table(name = "ipd_billing_settings")
@Data
@NoArgsConstructor
public class IpdBillingSetting {

    public static final String CALENDAR_DAY = "CALENDAR_DAY";
    public static final String HOURS_24 = "HOURS_24";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, unique = true)
    private Long hospitalId;

    /** Advance to collect at a planned (ELECTIVE) admission; null or 0 = none required. */
    @Column(name = "min_advance", precision = 10, scale = 2)
    private BigDecimal minAdvance;

    /** CALENDAR_DAY: each date is a day. HOURS_24: each 24 hours from admission is a day. */
    @Column(name = "bed_charge_rule", nullable = false, length = 20)
    private String bedChargeRule = CALENDAR_DAY;

    /** HOURS_24 only: a new day is charged once the stay runs this many hours past a full day. */
    @Column(name = "grace_hours", nullable = false)
    private Integer graceHours = 0;

    /** Charged when a doctor records a round, at most once per doctor per day; null or 0 = free. */
    @Column(name = "doctor_visit_fee", precision = 10, scale = 2)
    private BigDecimal doctorVisitFee;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
