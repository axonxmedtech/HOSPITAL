package com.hms.repository;

import com.hms.entity.NurseAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NurseAssessmentRepository extends JpaRepository<NurseAssessment, Long> {
    Optional<NurseAssessment> findByIpdAdmissionId(Long ipdAdmissionId);
    boolean existsByIpdAdmissionId(Long ipdAdmissionId);
}
