package com.hms.repository;

import com.hms.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    Optional<Payroll> findByHospitalIdAndEmployeeIdAndSalaryMonth(Long hospitalId, Long employeeId, String salaryMonth);

    List<Payroll> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
