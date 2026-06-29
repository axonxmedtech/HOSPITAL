package com.hms.repository;

import com.hms.entity.DoctorRound;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DoctorRoundRepository extends JpaRepository<DoctorRound, Long> {
    List<DoctorRound> findByIpdAdmissionIdAndHospitalIdOrderByRoundDateTimeDesc(Long ipdAdmissionId, Long hospitalId);
}
