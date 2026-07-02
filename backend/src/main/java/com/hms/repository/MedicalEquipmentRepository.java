package com.hms.repository;

import com.hms.entity.MedicalEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicalEquipmentRepository extends JpaRepository<MedicalEquipment, Long> {
    Optional<MedicalEquipment> findByIdAndHospitalId(Long id, Long hospitalId);

    Optional<MedicalEquipment> findByHospitalIdAndAssetCode(Long hospitalId, String assetCode);

    long countByHospitalId(Long hospitalId);

    List<MedicalEquipment> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
