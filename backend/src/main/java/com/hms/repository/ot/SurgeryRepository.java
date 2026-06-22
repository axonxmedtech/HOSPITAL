package com.hms.repository.ot;

import com.hms.entity.ot.Surgery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurgeryRepository extends JpaRepository<Surgery, Long> {
    List<Surgery> findByHospitalIdAndActiveTrueOrderByName(Long hospitalId);
}
