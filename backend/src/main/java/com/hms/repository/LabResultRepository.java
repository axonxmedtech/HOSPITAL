package com.hms.repository;

import com.hms.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * LabResultRepository — Data access for lab result records.
 * One result per order (enforced by UNIQUE on lab_order_id).
 */
public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    /** Fetch the result for a completed lab order */
    Optional<LabResult> findByLabOrderId(Long labOrderId);

    /** Guard check before inserting a duplicate result */
    boolean existsByLabOrderId(Long labOrderId);
}
