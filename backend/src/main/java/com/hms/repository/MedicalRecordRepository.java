package com.hms.repository;

import com.hms.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findByPatientId(Long patientId);

    Optional<MedicalRecord> findByAppointmentId(Long appointmentId);

    Optional<MedicalRecord> findByOpdId(Long opdId);

    List<MedicalRecord> findTop5ByPatientIdOrderByCreatedAtDesc(Long patientId);

    Optional<MedicalRecord> findTopByPatientIdOrderByCreatedAtDesc(Long patientId);
}
