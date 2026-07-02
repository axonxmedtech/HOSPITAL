package com.hms.service.hospital;

import com.hms.entity.IncidentReport;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IncidentReportRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncidentReportService {

    @Autowired
    private IncidentReportRepository incidentReportRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<IncidentReport> getIncidentReports() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return incidentReportRepository.findByHospitalId(hospitalId);
    }

    @Transactional
    public IncidentReport updateInvestigation(Long id, String status, String notes) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        IncidentReport incident = incidentReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident report not found: " + id));

        if (!incident.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        incident.setStatus(status);
        if (notes != null && !notes.isBlank()) {
            incident.setDescription(incident.getDescription() + "\n\nInvestigation: " + notes);
        }
        return incidentReportRepository.save(incident);
    }
}
