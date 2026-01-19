package com.hms.repository;

import com.hms.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByMedicalRecordId(Long medicalRecordId);

    List<Prescription> findByHospitalIdAndStatus(Long hospitalId, String status);

    // For Pharmacy Dashboard
    // For Pharmacy Dashboard
    // List<Prescription> findByHospitalIdAndStatus(Long hospitalId, String status);
}
