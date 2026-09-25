package com.hms.entity.import_;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The outcome of one spreadsheet row within a batch. Mirrors Flyway V22.
 *
 * <p>{@code rawRowJson} is the original row, kept ONLY when the state is NEEDS_REVIEW or FAILED:
 * it is what the correction loop re-uploads. It is patient data at rest and subject to the
 * 90-day retention rule (enforced in a later phase); success rows never carry it.
 *
 * <p>{@code matchedPatientId} is history ("matched PAT17 at the time") and deliberately not a
 * foreign key, so the record survives that patient later being purged.
 */
@Entity
@Table(name = "import_row_results")
@Data
@NoArgsConstructor
public class ImportRowResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 1-based spreadsheet row as the administrator sees it in Excel (row 1 is the header). */
    @Column(name = "row_num", nullable = false)
    private int rowNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportRowState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 40)
    private ImportReasonCode reasonCode;

    @Column(name = "column_name", length = 120)
    private String columnName;

    @Column(length = 500)
    private String message;

    @Column(name = "phone_masked", length = 15)
    private String phoneMasked;

    @Column(name = "matched_patient_id")
    private Long matchedPatientId;

    @Column(name = "raw_row_json", columnDefinition = "TEXT")
    private String rawRowJson;

    public ImportRowResult(Long batchId, int rowNum, ImportRowState state, ImportReasonCode reasonCode) {
        this.batchId = batchId;
        this.rowNum = rowNum;
        this.state = state;
        this.reasonCode = reasonCode;
    }
}
