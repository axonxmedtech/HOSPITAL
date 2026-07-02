package com.hms.repository;

import com.hms.entity.PatientAdvance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientAdvanceRepository extends JpaRepository<PatientAdvance, Long> {
    List<PatientAdvance> findByHospitalIdAndIpdAdmissionIdAndStatus(Long hospitalId, Long ipdAdmissionId, String status);

    List<PatientAdvance> findByHospitalIdAndIpdAdmissionIdOrderByReceivedAtDesc(Long hospitalId, Long ipdAdmissionId);
}
