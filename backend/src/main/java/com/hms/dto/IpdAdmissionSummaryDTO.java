package com.hms.dto;

import java.time.LocalDateTime;

public class IpdAdmissionSummaryDTO {
    private Long ipdId;
    private String ipdNumber;
    private Long patientId;
    private String patientName;
    private Integer age;
    private String gender;
    private String wardName;
    private String bedNumber;
    private String doctorName;
    private LocalDateTime admissionDateTime;
    private String status;
    private Boolean admissionConfirmed;
    /** In a critical-care bed now: the IPD list shows every admitted patient, ICU ones flagged. */
    private boolean inIcu;
    /** The ICU unit's type label (ICU, NICU, …) when {@link #inIcu}. */
    private String icuUnitLabel;

    public boolean isInIcu() { return inIcu; }
    public void setInIcu(boolean inIcu) { this.inIcu = inIcu; }

    public String getIcuUnitLabel() { return icuUnitLabel; }
    public void setIcuUnitLabel(String icuUnitLabel) { this.icuUnitLabel = icuUnitLabel; }

    public Boolean getAdmissionConfirmed() { return admissionConfirmed; }
    public void setAdmissionConfirmed(Boolean admissionConfirmed) { this.admissionConfirmed = admissionConfirmed; }

    public Long getIpdId() { return ipdId; }
    public void setIpdId(Long ipdId) { this.ipdId = ipdId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getIpdNumber() { return ipdNumber; }
    public void setIpdNumber(String ipdNumber) { this.ipdNumber = ipdNumber; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }

    public String getBedNumber() { return bedNumber; }
    public void setBedNumber(String bedNumber) { this.bedNumber = bedNumber; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public LocalDateTime getAdmissionDateTime() { return admissionDateTime; }
    public void setAdmissionDateTime(LocalDateTime admissionDateTime) { this.admissionDateTime = admissionDateTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
