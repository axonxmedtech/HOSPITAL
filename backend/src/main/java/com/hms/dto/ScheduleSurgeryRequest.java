package com.hms.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Reception's schedule payload: assign surgeon + date/time + OT ward. */
@Data
public class ScheduleSurgeryRequest {
    private Long surgeonDoctorId;   // a listed doctor; null when "Other" is chosen
    private String surgeonName;     // free-text operator name, used when surgeonDoctorId is null ("Other")
    private String anaesthetistName; // optional anaesthetist present for the surgery
    private LocalDateTime scheduledAt; // required
    private Long otWardId;          // required (ward named "OT")
}
