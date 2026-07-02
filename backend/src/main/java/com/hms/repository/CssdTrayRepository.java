package com.hms.repository;

import com.hms.entity.CssdTray;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CssdTrayRepository extends JpaRepository<CssdTray, Long> {
    Optional<CssdTray> findByIdAndHospitalId(Long id, Long hospitalId);

    Optional<CssdTray> findByHospitalIdAndBarcode(Long hospitalId, String barcode);

    List<CssdTray> findByHospitalIdAndCycleId(Long hospitalId, Long cycleId);

    List<CssdTray> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
