package com.hms.repository;

import com.hms.entity.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    List<IncidentReport> findByHospitalId(Long hospitalId);
    List<IncidentReport> findByHospitalIdAndSource(Long hospitalId, String source);
}
