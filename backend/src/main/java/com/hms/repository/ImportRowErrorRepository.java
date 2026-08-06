package com.hms.repository;

import com.hms.entity.ImportRowError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
    Page<ImportRowError> findByBatchId(Long batchId, Pageable pageable);
    List<ImportRowError> findByBatchIdOrderByRowNumberAsc(Long batchId);
    void deleteByBatchId(Long batchId);
}
