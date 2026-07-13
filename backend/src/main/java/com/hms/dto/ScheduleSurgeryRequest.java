package com.hms.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Schedule payload: assign surgeon + date/time + theatre. */
@Data
public class ScheduleSurgeryRequest {
    private Long surgeonDoctorId;   // a listed doctor; null when "Other" is chosen
    private String surgeonName;     // free-text operator name, used when surgeonDoctorId is null ("Other")
    private String anaesthetistName; // optional anaesthetist present for the surgery
    private LocalDateTime scheduledAt; // required
    /** The theatre. Preferred. */
    private Long otRoomId;
    /** Legacy: a ward whose name contained "OT". Resolved to its room when one was migrated. */
    private Long otWardId;
    /** Drives interval booking; the clash query defaults to 60 minutes when absent. */
    private Integer estimatedDurationMinutes;
}
