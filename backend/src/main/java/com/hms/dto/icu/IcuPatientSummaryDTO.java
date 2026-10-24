package com.hms.dto.icu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IcuPatientSummaryDTO {
    private Long ipdId;
    private String ipdNumber;
    private Long patientId;
    private String patientName;
    private Object age;
    private String gender;
    private Long icuWardId;
    private String icuWardName;
    private String unitType;
    private Long bedId;
    private String bedNumber;
    private Long doctorId;
    private String doctorName;
    private Long intensivistDoctorId;
    private String intensivistName;
    private LocalDateTime admissionDateTime;
    private String status;
    private String stayPublicId;
    private String admissionReason;
}
