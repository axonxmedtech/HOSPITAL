package com.hms.dto;

import lombok.Data;

/**
 * Create/update payload for a nursing note.
 */
@Data
public class NursingNoteRequest {
    private Long ipdAdmissionId; // required on create; ignored on update
    private String noteText;
    private String category;     // optional (reserved)
    private Long surgeryId;       // optional — links an OT note to its surgery
}
