package com.hms.dto; import lombok.Data; import java.time.LocalDate; import java.util.List;
@Data public class RangeFillShiftRequest {
    private Long nurseProfileId; private LocalDate fromDate; private LocalDate toDate;
    private String shiftTemplatePublicId; private List<Integer> daysOfWeek; // 1=Mon..7=Sun; null/empty = all days
}
