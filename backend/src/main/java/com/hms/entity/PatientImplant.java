package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Implant / Prosthesis / Biomedical Device Record (Form 24 core).
 * Multiple implants can be recorded per booking (1:N with OtBooking —
 * the one deviation from the 1:1 OT recipe).
 *
 * v1: traceability record only. Stock deduction (BR-3) and auto-billing
 * (BR-4) are deferred fan-outs per §4.5 in handoff-2.
 *
 * Gate (§4.1): implants may only be added while an OperationRecord exists
 * and is NOT FINALIZED.
 *
 * Additive/nullable columns — Hibernate ddl-auto creates them.
 */
@Entity
@Table(name = "patient_implant")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientImplant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "ot_booking_id", nullable = false)
    private Long otBookingId;

    /** Nullable FK to InventoryItem master catalog. */
    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(name = "implant_name", length = 150)
    private String implantName;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model_number", length = 50)
    private String modelNumber;

    @Column(name = "catalog_number", length = 50)
    private String catalogNumber;

    /** Indexed for recall searches. */
    @Column(name = "batch_number", length = 30)
    private String batchNumber;

    @Column(name = "lot_number", length = 30)
    private String lotNumber;

    /** Must be unique per tenant (BR-7). Enforced in service, not via unique constraint
     *  to allow multi-tenant flexibility. */
    @Column(name = "serial_number", length = 40)
    private String serialNumber;

    /** Unique Device Identifier (GS1/HIBC). */
    @Column(name = "udi", length = 100)
    private String udi;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity_opened")
    private Integer quantityOpened;

    @Column(name = "quantity_implanted")
    private Integer quantityImplanted;

    @Column(name = "quantity_returned")
    private Integer quantityReturned;

    @Column(name = "quantity_wasted")
    private Integer quantityWasted;

    /** Anatomical implant site. */
    @Column(name = "implant_location", length = 100)
    private String implantLocation;

    @Column(name = "warranty_card_number", length = 50)
    private String warrantyCardNumber;

    @Column(name = "patient_card_issued")
    private Boolean patientCardIssued;

    /** Nurse signature — base64 blob or text marker. */
    @Column(name = "nurse_sig", columnDefinition = "text")
    private String nurseSig;

    /** Surgeon sign-off — triggers deferred fan-outs (BR-3/4). */
    @Column(name = "surgeon_sig", columnDefinition = "text")
    private String surgeonSig;

    @Column(name = "signed_by")
    private Long signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** DRAFT | SIGNED */
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_by_name", length = 100)
    private String recordedByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
