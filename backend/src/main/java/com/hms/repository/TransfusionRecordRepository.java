package com.hms.repository;

import com.hms.entity.TransfusionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransfusionRecordRepository extends JpaRepository<TransfusionRecord, Long> {
    Optional<TransfusionRecord> findByHospitalIdAndBloodUnitId(Long hospitalId, Long bloodUnitId);

    List<TransfusionRecord> findByHospitalIdAndPatientIdOrderByStartedAtDesc(Long hospitalId, Long patientId);

    List<TransfusionRecord> findByHospitalIdAndPatientIdAndCompletedAtIsNull(Long hospitalId, Long patientId);
}
