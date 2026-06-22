package com.hms.repository.ot;

import com.hms.entity.ot.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByHospitalIdOrderByName(Long hospitalId);
    long countByHospitalIdAndStatusIgnoreCase(Long hospitalId, String status);
}
