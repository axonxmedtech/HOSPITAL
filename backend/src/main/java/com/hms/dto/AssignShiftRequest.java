package com.hms.dto; import lombok.Data; import java.time.LocalDate;
@Data public class AssignShiftRequest { private Long nurseProfileId; private LocalDate date; private String shiftTemplatePublicId; }
