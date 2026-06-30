package com.hms.dto;

import java.time.LocalDateTime;

public class MrdPendingDTO {
    public Long ipdAdmissionId;
    public String ipdNumber;
    public String patientName;
    public String patientGender;
    public Integer patientAge;
    public String doctorName;
    public LocalDateTime admissionDateTime;
    public LocalDateTime dischargeDateTime;
}
