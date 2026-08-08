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

        long countByHospitalIdAndIsActiveTrue(Long hospitalId);

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

        List<Patient> findByHospitalIdAndIsActiveTrue(Long hospitalId);

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

        /**
         * Count active patients created within a date range
         * Used for "Patients This Month" and "Patients Today" stats
         */
        long countByHospitalIdAndIsActiveTrueAndCreatedAtBetween(
                        Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);

        /**
         * Find a patient (active or soft-deleted) by legacy ID within a hospital.
         * Deliberately does NOT filter on isActive: when an import batch is undone,
         * rows are soft-deleted but keep their legacy_id, which still occupies the
         * unique (hospital_id, legacy_id) index. Matching soft-deleted rows lets a
         * corrected re-import reactivate them instead of colliding on that index.
         */
        Optional<Patient> findByHospitalIdAndLegacyId(Long hospitalId, String legacyId);

        /**
         * All patients written by a given import batch. Used by undo to find what to
         * soft-delete and by the clinical-activity guard to check what must be kept.
         */
        List<Patient> findByImportBatchId(Long importBatchId);
}
