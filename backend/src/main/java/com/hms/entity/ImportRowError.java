package com.hms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "import_row_error")
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

    public ImportRowError() { }

    public ImportRowError(Long batchId, Integer rowNumber, String columnName, String message, String rawRowJson) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
        this.rawRowJson = rawRowJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRawRowJson() { return rawRowJson; }
    public void setRawRowJson(String rawRowJson) { this.rawRowJson = rawRowJson; }
}
