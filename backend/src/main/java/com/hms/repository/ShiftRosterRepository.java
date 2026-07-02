package com.hms.repository;

import com.hms.entity.ShiftRoster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftRosterRepository extends JpaRepository<ShiftRoster, Long> {
    Optional<ShiftRoster> findByHospitalIdAndEmployeeIdAndDate(Long hospitalId, Long employeeId, LocalDate date);

    List<ShiftRoster> findByHospitalIdAndEmployeeIdAndDateBetween(Long hospitalId, Long employeeId, LocalDate start, LocalDate end);

    List<ShiftRoster> findByHospitalIdOrderByDateDesc(Long hospitalId);
}
