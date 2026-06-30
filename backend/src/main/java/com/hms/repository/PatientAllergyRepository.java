package com.hms.repository;

import com.hms.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {
    List<PatientAllergy> findByPatientIdAndHospitalId(Long patientId, Long hospitalId);
    boolean existsByPatientIdAndAllergyMasterId(Long patientId, Long allergyMasterId);

    @Query("SELECT a.allergyName FROM PatientAllergy pa JOIN AllergyMaster a ON pa.allergyMasterId = a.id " +
           "WHERE pa.patientId = :patientId AND pa.hospitalId = :hospitalId AND a.isActive = true")
    List<String> findAllergyNamesByPatientId(@Param("patientId") Long patientId,
                                              @Param("hospitalId") Long hospitalId);
}
