package com.hms.service.pdf;

import com.hms.dto.PlanDischargeRequest.DischargeMedication;
import com.hms.entity.DischargeSummary;
import com.hms.entity.Doctor;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Surgery;

import java.time.LocalTime;
import java.util.List;

/** Everything printed on a discharge summary, gathered (tenant-scoped) by the IPD service. */
public class DischargeSummaryPrint {
    public IpdAdmission ipd;
    public DischargeSummary summary;
    public List<DischargeMedication> medicines;
    public Doctor doctor;
    public String ward;
    public String bed;
    public List<Surgery> surgeries;
    /** The booked follow-up time, when the visit was booked at discharge. */
    public LocalTime followUpTime;
}
