package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.NarcoticLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NarcoticLogRepository extends JpaRepository<NarcoticLog, Long> {
    List<NarcoticLog> findByHospitalIdAndPharmacySaleId(Long hospitalId, Long pharmacySaleId);

    List<NarcoticLog> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
