package com.hms.dto; import lombok.Data; import java.time.LocalTime;
@Data public class ShiftTemplateRequest { private String name; private LocalTime startTime; private LocalTime endTime; }
