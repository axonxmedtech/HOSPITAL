package com.hms.repository;

import com.hms.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * HospitalRepository - Data access layer for Hospital entity
 * 
 * This repository provides database operations for managing hospitals.
 * Only Super Admin uses this repository.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    /**
     * Find all hospitals ordered by creation date (newest first)
     * 
     * @return List of all hospitals
     */
    Optional<Hospital> findByPublicId(String publicId);

    /**
     * Find all hospitals ordered by creation date (newest first)
     * 
     * @return List of all hospitals
     */
    List<Hospital> findAllByOrderByCreatedAtDesc();

    /**
     * Find all hospitals ordered by creation date with pagination
     */
    org.springframework.data.domain.Page<Hospital> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
