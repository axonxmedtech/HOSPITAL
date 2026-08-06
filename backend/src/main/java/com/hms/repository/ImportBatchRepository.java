package com.hms.repository;

import com.hms.entity.ImportBatch;
import com.hms.entity.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
    List<ImportBatch> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    boolean existsByHospitalIdAndStatus(Long hospitalId, ImportStatus status);
}
