package com.hms.dto;

import java.time.LocalDateTime;

public class MrdArchivedDTO {
    public Long id;
    public Long ipdAdmissionId;
    public String ipdNumber;
    public String mrdNumber;
    public String rackLocation;
    public String patientName;
    public String doctorName;
    public LocalDateTime archivedAt;
    public String archivedByName;
}
