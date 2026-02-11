package com.hms.repository;

import com.hms.entity.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
    List<LabOrder> findByMedicalRecordId(Long medicalRecordId);
}
