package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Prescription - Entity to store medicines prescribed during a visit
 * 
 * Each prescription item (medicine) is a row here.
 * Linked to a MedicalRecord.
 * 
 * @author HMS Team
 * @version Phase-3
 */
@Entity
@Table(name = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "medical_record_id", nullable = false)
    private Long medicalRecordId;

    @Column(nullable = false)
    private String medicineName;

    /**
     * The facility medicine this order refers to, when the prescriber picked one from stock.
     *
     * <p>Deliberately nullable, and deliberately never inferred. A doctor must be able to
     * prescribe something the facility does not stock -- that is ordinary practice, not an error
     * -- so the order stands on {@link #medicineName} alone and this stays null. Filling it in by
     * matching the name against the inventory would attach a clinical order to whichever row
     * happened to sort first, which is a worse answer than admitting the link is not there.
     *
     * <p>Set, it means someone chose that row: stock and expiry can be shown against the order,
     * and a dispense can be posted for it. Null means UNLINKED, and the medication still appears
     * on the chart and is still administered -- what is unknown is the stock, not the medicine.
     */
    @Column(name = "medicine_id")
    private Long medicineId;

    /**
     * Where a given dose is taken from: MEDICINE (medicine inventory, by {@link #medicineId}),
     * HOSPITAL_ITEM (hospital inventory, by {@link #hospitalItemName} — saline, IV fluids,
     * consumables), or null when the item is not stocked and the patient brings it.
     */
    @Column(name = "stock_source", length = 20)
    private String stockSource;

    @Column(name = "hospital_item_name", length = 150)
    private String hospitalItemName;

    /** Units taken from stock per dose (1 tablet, 1 bottle). */
    @Column(name = "quantity_per_dose")
    private Integer quantityPerDose;

    @Column(length = 50)
    private String dosage; // the dose taken each time, e.g. "1/2 tablet"

    /** Strength of the medicine, e.g. "500 mg", "250 mg/5 ml". Empty on older prescriptions. */
    @Column(name = "strength", length = 50)
    private String strength;

    @Column(length = 50)
    private String frequency; // e.g., "1-0-1" (Morning-Afternoon-Night)

    @Column(length = 50)
    private String duration; // e.g., "5 Days"

    @Column(name = "duration_days")
    private Integer durationDays; // numeric days where applicable

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(length = 200)
    private String instructions; // free text: anything not covered by the fields below

    /**
     * When the dose is taken relative to food: BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, NOT_SPECIFIED.
     *
     * <p>Its own column, deliberately, rather than a controlled vocabulary squeezed into
     * {@link #instructions}. That field is general -- "take with plenty of water", "crush before
     * giving" -- and turning it into a four-value dropdown would have removed the ability to
     * record any of that. Food timing was only ever a convention inside it, prompted by a
     * placeholder, so it was never reliably readable.
     *
     * <p>Nullable, and no backfill: an existing order's food timing is unknown, and inferring it
     * by reading old free text would be guessing at a medication instruction. Historical rows keep
     * showing whatever their instructions say, and the value is stored as a plain string so an
     * unrecognised legacy value renders instead of breaking deserialisation.
     */
    @Column(name = "food_timing", length = 20)
    private String foodTiming;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / STOPPED / COMPLETED

    @Column(name = "type", nullable = false)
    private String type = "TABLET"; // TABLET / INJECTION / IV_FLUID

    @Column(name = "route", nullable = false)
    private String route = "ORAL"; // ORAL / IV / IM

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Duration is kept both as text ("5 Days", shown and printed) and as a number of days (used
     * for the chart and stopping). Whichever one a screen filled in, the other is derived, so no
     * list shows a medicine without its days.
     */
    @PrePersist
    @PreUpdate
    void alignDuration() {
        boolean noText = duration == null || duration.isBlank();
        if (noText && durationDays != null && durationDays > 0) {
            duration = durationDays + (durationDays == 1 ? " Day" : " Days");
        } else if (!noText && durationDays == null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^\\s*(\\d{1,3})\\s*(d|day|days)?\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(duration);
            if (m.matches()) durationDays = Integer.parseInt(m.group(1));
        }
    }
}
