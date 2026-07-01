package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * NursingProcedure - Entity representing structured non-medication nursing procedures
 * performed during a shift.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "nursing_procedure")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NursingProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "progress_note_id", nullable = false)
    private Long progressNoteId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "procedure_name", nullable = false, length = 150)
    private String procedureName; // e.g. "Dressing", "Catheter Care", "Nebulization"

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "performed_time", nullable = false)
    private LocalDateTime performedTime;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;
}
