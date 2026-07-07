package com.hms.repository;

import com.hms.entity.MedicationAdministration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicationAdministrationRepository extends JpaRepository<MedicationAdministration, Long> {
    List<MedicationAdministration> findByIpdAdmissionIdAndIsActiveTrueOrderByCreatedAtDesc(Long ipdAdmissionId);
    List<MedicationAdministration> findByPrescriptionIdAndIsActiveTrueOrderByCreatedAtDesc(Long prescriptionId);
}
