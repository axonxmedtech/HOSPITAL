package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * SignatureSlot - Entity representing a polymorphic signature slot.
 * Captures names, roles, relationships (witness/guardian/translator), and signature image/hash
 * associated with any document flow.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Entity
@Table(name = "signature_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignatureSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "signer_role", nullable = false, length = 30)
    private String signerRole; // e.g. "PATIENT", "DOCTOR", "WITNESS", "GUARDIAN", "TRANSLATOR"

    @Column(name = "signer_name", nullable = false, length = 100)
    private String signerName;

    @Column(name = "signer_relationship", length = 50)
    private String signerRelationship; // e.g. "Spouse", "Son", "Friend" (applicable for witnesses/guardians)

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // e.g. "CONSENT_GENERAL", "DISCHARGE_SUMMARY"

    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId; // Links logically to the target document (using its publicId / code)

    @Column(name = "signature_image_base64", columnDefinition = "longtext")
    private String signatureImageBase64; // Signature draw representation

    @Column(name = "signature_hash", length = 64)
    private String signatureHash; // Hash check to preserve document integrity
}
