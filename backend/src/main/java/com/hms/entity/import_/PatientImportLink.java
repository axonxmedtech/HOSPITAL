package com.hms.entity.import_;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Import provenance for one patient — everything an import needs to remember, kept OFF the
 * patients table. One row per imported patient ({@code patientId} is unique); a patient that
 * reception registered by hand has no link at all.
 *
 * <p>Mirrors Flyway V23. Semantics that matter:
 * <ul>
 *   <li>{@code legacyId} — the source system's MRN, unique per hospital; the deterministic match
 *       key for re-imports. NULL when the file had no MRN column (NULLs never collide).</li>
 *   <li>{@code createdByBatchId} — the batch that CREATED the patient. Undo reverses exactly these
 *       rows; a later batch that merely updated the patient never re-stamps it.</li>
 *   <li>{@code lastImportedValuesJson} — the demographic values the importer wrote last time. On
 *       re-import, a field whose current value differs from this was changed by a human in HMS
 *       since, and the row goes to review instead of being overwritten. Value-based, so it holds
 *       for every write path without anyone setting a flag.</li>
 * </ul>
 *
 * <p>Batch ids carry no foreign key on purpose (history pointers); the patient reference cascades
 * with the patient row, which is what keeps the tenant purge working unchanged.
 */
@Entity
@Table(name = "patient_import_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_patient_import_link_patient", columnNames = "patient_id"),
                @UniqueConstraint(name = "uk_patient_import_link_legacy", columnNames = {"hospital_id", "legacy_id"})
        })
@Data
@NoArgsConstructor
public class PatientImportLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "legacy_id", length = 100)
    private String legacyId;

    @Column(name = "created_by_batch_id")
    private Long createdByBatchId;

    @Column(name = "last_batch_id", nullable = false)
    private Long lastBatchId;

    @Column(name = "last_imported_at", nullable = false)
    private LocalDateTime lastImportedAt;

    @Column(name = "last_imported_values_json", columnDefinition = "TEXT")
    private String lastImportedValuesJson;

    @Column(name = "custom_fields_json", columnDefinition = "TEXT")
    private String customFieldsJson;
}
