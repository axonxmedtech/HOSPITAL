package com.hms.repository;

import com.hms.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {
    List<VitalSigns> findByIpdAdmissionIdOrderByRecordedAtDesc(Long ipdAdmissionId);
}
