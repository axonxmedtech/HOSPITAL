package com.hms.dto; import lombok.Data; import java.time.LocalTime;
@Data public class AppointmentSlotRequest { private LocalTime startTime; private LocalTime endTime; }
