package com.hms.dto;

import lombok.Data;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.hms.validation.PersonName;

/** Schedule payload: assign surgeon + date/time + theatre. */
@Data
public class ScheduleSurgeryRequest {
    private Long surgeonDoctorId;   // a listed doctor; null when "Other" is chosen

    @PersonName
    private String surgeonName;     // free-text operator name, used when surgeonDoctorId is null ("Other")

    @PersonName
    private String anaesthetistName; // optional anaesthetist present for the surgery

    @NotNull(message = "Scheduled time is required")
    private LocalDateTime scheduledAt; // required
    /** The theatre. Preferred. */
    private Long otRoomId;
    /** Legacy: a ward whose name contained "OT". Resolved to its room when one was migrated. */
    private Long otWardId;
    /** Drives interval booking; the clash query defaults to 60 minutes when absent. */
    @Positive(message = "Estimated duration must be positive")
    private Integer estimatedDurationMinutes;
}
