package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "import_row_error")
@Data
@NoArgsConstructor
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** Column is row_num, not row_number: ROW_NUMBER is reserved in MySQL 8.0+. */
    @Column(name = "row_num", nullable = false)
    private Integer rowNumber;

    @Column(name = "column_name", length = 120)
    private String columnName;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "raw_row_json", columnDefinition = "text")
    private String rawRowJson;

    public ImportRowError(Long batchId, Integer rowNumber, String columnName, String message, String rawRowJson) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
        this.rawRowJson = rawRowJson;
    }
}
