package com.hms.repository;

import com.hms.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByIdAndHospitalId(Long id, Long hospitalId);

    Optional<Employee> findByHospitalIdAndUserId(Long hospitalId, Long userId);

    long countByHospitalId(Long hospitalId);

    List<Employee> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
