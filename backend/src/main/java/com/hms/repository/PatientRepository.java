package com.hms.repository;

import com.hms.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PatientRepository - Data access layer for Patient entity
 * 
 * This repository provides database operations for managing patients.
 * All queries automatically filter by hospital_id for multi-tenant isolation.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

        long countByHospitalId(Long hospitalId);

        /**
         * Find all patients belonging to a specific hospital
         * Used to list patients for a hospital (multi-tenant filtering)
         * 
         * @param hospitalId Hospital ID to filter by
         * @return List of patients for the hospital
         */
        /**
         * Find all active patients belonging to a specific hospital
         * Used to list patients for a hospital (multi-tenant filtering)
         * 
         * @param hospitalId Hospital ID to filter by
         * @return List of active patients for the hospital
         */
        org.springframework.data.domain.Page<Patient> findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(
                        Long hospitalId,
                        org.springframework.data.domain.Pageable pageable);

        List<Patient> findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(Long hospitalId);

        /**
         * Find an active patient by ID and hospital ID
         * Ensures multi-tenant isolation - patient must belong to the hospital
         * 
         * @param id         Patient ID
         * @param hospitalId Hospital ID to filter by
         * @return Optional containing the patient if found, active, and belongs to the
         *         hospital
         */
        Optional<Patient> findByIdAndHospitalIdAndIsActiveTrue(Long id, Long hospitalId);

        Optional<Patient> findByPublicIdAndHospitalIdAndIsActiveTrue(String publicId, Long hospitalId);

        /**
         * Search active patients by name or phone
         * 
         * @param hospitalId Hospital ID
         * @param name       Name search term
         * @param phone      Phone search term
         * @return List of matching patients
         */
        List<Patient> findByHospitalIdAndIsActiveTrueAndNameContainingIgnoreCaseOrHospitalIdAndIsActiveTrueAndPhoneContaining(
                        Long hospitalId, String name, Long hospitalId2, String phone);

        /**
         * Find patient by phone number and hospital ID
         * Used to check if patient already exists before creating new one
         * 
         * @param phone      Phone number
         * @param hospitalId Hospital ID
         * @return Optional containing the patient if found
         */
        List<Patient> findByPhoneAndHospitalIdAndIsActiveTrue(String phone, Long hospitalId);

        /**
         * Find active patients created within a date range (for Today filter)
         */
        Optional<Patient> findByPublicId(String publicId);

        org.springframework.data.domain.Page<Patient> findByHospitalIdAndIsActiveTrueAndCreatedAtBetweenOrderByCreatedAtDesc(
                        Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end,
                        org.springframework.data.domain.Pageable pageable);
}
