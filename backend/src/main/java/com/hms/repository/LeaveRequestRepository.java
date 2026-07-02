package com.hms.repository;

import com.hms.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    Optional<LeaveRequest> findByIdAndHospitalId(Long id, Long hospitalId);

    List<LeaveRequest> findByHospitalIdAndEmployeeId(Long hospitalId, Long employeeId);

    List<LeaveRequest> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
