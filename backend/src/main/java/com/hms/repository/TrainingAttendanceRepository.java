package com.hms.repository;

import com.hms.entity.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {
    Optional<TrainingAttendance> findByIdAndHospitalId(Long id, Long hospitalId);

    Optional<TrainingAttendance> findByHospitalIdAndSessionIdAndEmployeeId(Long hospitalId, Long sessionId, Long employeeId);

    List<TrainingAttendance> findByHospitalIdAndSessionId(Long hospitalId, Long sessionId);

    List<TrainingAttendance> findByHospitalIdAndEmployeeId(Long hospitalId, Long employeeId);

    List<TrainingAttendance> findByHospitalIdOrderByIdDesc(Long hospitalId);
}
