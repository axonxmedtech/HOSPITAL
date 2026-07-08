package com.hms.dto;

import com.hms.entity.Surgery;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read projection for OT lists/boards: the Surgery plus joined display fields
 * (patient, IPD number, surgeon name, ward name) so the frontend needs no extra
 * lookups.
 */
@Data
public class SurgeryView {
    private String publicId;
    private Long surgeryId;
    private Long ipdAdmissionId;
    private String ipdNumber;
    private Long patientId;
    private String patientName;
    private Integer patientAge;
    private String patientSex;
    private String procedureName;
    private String clinicalNotes;
    private String priority;
    private LocalDate preferredDate;
    private String status;
    private Long surgeonDoctorId;
    private String surgeonName;
    private String anaesthetistName;
    private LocalDateTime scheduledAt;
    private Long otWardId;
    private String otWardName;
    private String requestedByName;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static SurgeryView of(Surgery s) {
        SurgeryView v = new SurgeryView();
        v.publicId = s.getPublicId();
        v.surgeryId = s.getId();
        v.ipdAdmissionId = s.getIpdAdmissionId();
        v.patientId = s.getPatientId();
        v.procedureName = s.getProcedureName();
        v.clinicalNotes = s.getClinicalNotes();
        v.priority = s.getPriority();
        v.preferredDate = s.getPreferredDate();
        v.status = s.getStatus();
        v.surgeonDoctorId = s.getSurgeonDoctorId();
        v.anaesthetistName = s.getAnaesthetistName();
        v.scheduledAt = s.getScheduledAt();
        v.otWardId = s.getOtWardId();
        v.requestedAt = s.getRequestedAt();
        v.startedAt = s.getStartedAt();
        v.completedAt = s.getCompletedAt();
        return v;
    }
}
