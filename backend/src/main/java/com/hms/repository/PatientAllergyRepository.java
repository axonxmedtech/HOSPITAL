package com.hms.repository;

import com.hms.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {
    List<PatientAllergy> findByPatientIdAndHospitalId(Long patientId, Long hospitalId);
    boolean existsByPatientIdAndAllergyMasterId(Long patientId, Long allergyMasterId);
}
