package com.hms.repository;

import com.hms.entity.PatientPortalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientPortalUserRepository extends JpaRepository<PatientPortalUser, Long> {
    Optional<PatientPortalUser> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
