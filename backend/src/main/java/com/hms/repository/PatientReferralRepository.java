package com.hms.repository;

import com.hms.entity.PatientReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PatientReferralRepository extends JpaRepository<PatientReferral, Long> {
    List<PatientReferral> findByHospitalIdAndAdmissionId(Long hospitalId, Long admissionId);
    Optional<PatientReferral> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
}
