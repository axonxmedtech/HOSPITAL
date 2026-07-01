package com.hms.repository;

import com.hms.entity.PreAnaesthesiaAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreAnaesthesiaAssessmentRepository extends JpaRepository<PreAnaesthesiaAssessment, Long> {
    Optional<PreAnaesthesiaAssessment> findByAdmissionIdAndHospitalId(Long admissionId, Long hospitalId);
}
