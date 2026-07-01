package com.hms.repository;

import com.hms.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DoctorRepository - Data access layer for Doctor entity
 * 
 * This repository provides database operations for managing doctors.
 * All queries automatically filter by hospital_id for multi-tenant isolation.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /**
     * Find all doctors belonging to a specific hospital
     * Used to list doctors for a hospital (multi-tenant filtering)
     * 
     * @param hospitalId Hospital ID to filter by
     * @return List of doctors for the hospital
     */
    /**
     * Find all active doctors belonging to a specific hospital
     * Used to list doctors for a hospital (multi-tenant filtering)
     * 
     * @param hospitalId Hospital ID to filter by
     * @return List of active doctors for the hospital
     */
    org.springframework.data.domain.Page<Doctor> findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(Long hospitalId,
            org.springframework.data.domain.Pageable pageable);

    List<Doctor> findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(Long hospitalId);

    /**
     * Find an active doctor by ID and hospital ID
     * Ensures multi-tenant isolation - doctor must belong to the hospital
     * 
     * @param id         Doctor ID
     * @param hospitalId Hospital ID to filter by
     * @return Optional containing the doctor if found, active, and belongs to the
     *         hospital
     */
    Optional<Doctor> findByIdAndHospitalIdAndIsActiveTrue(Long id, Long hospitalId);

    Optional<Doctor> findByPublicIdAndHospitalIdAndIsActiveTrue(String publicId, Long hospitalId);

    /**
     * Find a doctor by email and hospital ID
     * Used to check if doctor email already exists in the hospital
     * 
     * @param email      Doctor's email
     * @param hospitalId Hospital ID to filter by
     * @return Optional containing the doctor if found
     */
    Optional<Doctor> findByEmailAndHospitalId(String email, Long hospitalId);

    Optional<Doctor> findByHospitalIdAndUserId(Long hospitalId, Long userId);

    default Optional<Doctor> findByIdOrUserId(Long id, UserRepository userRepository) {
        if (id == null) return Optional.empty();
        Optional<Doctor> doc = findById(id);
        if (doc.isPresent()) {
            return doc;
        }
        return userRepository.findById(id)
                .flatMap(user -> findByEmailAndHospitalId(user.getEmail(), user.getHospitalId()));
    }

    /**
     * Search active doctors by name or specialization
     * 
     * @param hospitalId Hospital ID
     * @param name       Name search term
     * @param spec       Specialization search term
     * @return List of matching doctors
     */
    List<Doctor> findByHospitalIdAndIsActiveTrueAndNameContainingIgnoreCaseOrHospitalIdAndIsActiveTrueAndSpecializationContainingIgnoreCase(
            Long hospitalId, String name, Long hospitalId2, String spec);
}
