package com.hms.repository;

import com.hms.entity.CalibrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, Long> {
    Optional<CalibrationRecord> findTopByHospitalIdAndEquipmentIdOrderByCalibrationDateDesc(Long hospitalId, Long equipmentId);

    List<CalibrationRecord> findByHospitalIdOrderByCalibrationDateDesc(Long hospitalId);
}
