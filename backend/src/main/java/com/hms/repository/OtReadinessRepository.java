package com.hms.repository;

import com.hms.entity.OtReadiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface OtReadinessRepository extends JpaRepository<OtReadiness, Long> {
    Optional<OtReadiness> findByOtRoomAndReadinessDateAndHospitalId(String otRoom, LocalDate readinessDate, Long hospitalId);
}
