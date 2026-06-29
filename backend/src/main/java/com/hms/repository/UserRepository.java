package com.hms.repository;

import com.hms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository - Data access layer for User entity
 * 
 * This repository provides database operations for managing users.
 * Used for authentication and user management across the system.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

        /**
         * Find a user by email address
         * Used for login authentication
         * 
         * @param email Email address to search for
         * @return Optional containing the user if found
         */
        Optional<User> findByEmail(String email);

        /**
         * Check if a user with the given email already exists
         * Used to prevent duplicate email registration
         * 
         * @param email Email address to check
         * @return true if email exists, false otherwise
         */
        boolean existsByEmail(String email);

        /**
         * Find active users by hospital ID and role
         * Used for fetching lists like Receptionists
         */
        Optional<User> findByPublicId(String publicId);

        /**
         * Find active users by hospital ID and role
         * Used for fetching lists like Receptionists
         */
        java.util.List<User> findByHospitalIdAndRoleAndIsActiveTrue(Long hospitalId, String role);

        /**
         * Find active users by hospital ID and role with pagination
         */
        org.springframework.data.domain.Page<User> findByHospitalIdAndRoleAndIsActiveTrue(Long hospitalId, String role,
                        org.springframework.data.domain.Pageable pageable);

        @Query("""
                            SELECT u FROM User u
                            WHERE u.hospitalId = :hospitalId
                              AND u.role = :role
                              AND u.isActive = true
                              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        org.springframework.data.domain.Page<User> searchPharmacists(Long hospitalId, String role, String search,
                        org.springframework.data.domain.Pageable pageable);
        // ...existing code...

        /**
         * Find users by hospital ID and role (Active or Inactive)
         */
        java.util.List<User> findByHospitalIdAndRole(Long hospitalId, String role);

        @Query("""
                            SELECT u FROM User u
                            WHERE u.hospitalId = :hospitalId
                              AND u.role = :role
                              AND u.isActive = true
                              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        org.springframework.data.domain.Page<User> searchReceptionists(Long hospitalId, String role, String search,
                        org.springframework.data.domain.Pageable pageable);

        @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(custom_id, 4) AS UNSIGNED)), 0) FROM users WHERE role = 'RECEPTIONIST' AND custom_id LIKE 'REC%'", nativeQuery = true)
        Integer findMaxReceptionistSequence();

        @Query("""
                            SELECT u FROM User u
                            WHERE u.hospitalId = :hospitalId
                              AND u.role = :role
                              AND u.isActive = true
                              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        org.springframework.data.domain.Page<User> searchNurses(Long hospitalId, String role, String search,
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT new com.hms.dto.UserSummaryDTO(u, h.name) FROM User u LEFT JOIN Hospital h ON u.hospitalId = h.id "
                        +
                        "WHERE (:role IS NULL OR u.role = :role OR (:role = 'HOSPITAL_ADMIN' AND u.role = 'ADMIN')) " +
                        "AND (:hospitalId IS NULL OR h.publicId = :hospitalId) " +
                        "AND (:search IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) "
                        +
                        "AND u.role != 'SUPER_ADMIN'")
        org.springframework.data.domain.Page<com.hms.dto.UserSummaryDTO> findAllSummary(
                        @org.springframework.data.repository.query.Param("role") String role,
                        @org.springframework.data.repository.query.Param("hospitalId") String hospitalId,
                        @org.springframework.data.repository.query.Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("""
                            SELECT u FROM User u
                            WHERE u.hospitalId = :hospitalId
                              AND u.role = :role
                              AND u.isActive = true
                              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        org.springframework.data.domain.Page<User> searchLabTechnicians(
                        @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
                        @org.springframework.data.repository.query.Param("role") String role,
                        @org.springframework.data.repository.query.Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Returns the highest sequential number used by LAB_TECHNICIAN custom IDs (LT1, LT2, …).
         * Used to derive next custom ID without a gap.
         */
        @org.springframework.data.jpa.repository.Query(
                value = "SELECT COALESCE(MAX(CAST(SUBSTRING(custom_id, 3) AS UNSIGNED)), 0) " +
                        "FROM users WHERE role = 'LAB_TECHNICIAN' AND custom_id LIKE 'LT%'",
                nativeQuery = true)
        Integer findMaxLabTechSequence();

        @org.springframework.data.jpa.repository.Query("""
                            SELECT u FROM User u
                            WHERE u.hospitalId = :hospitalId
                              AND u.role = :role
                              AND u.isActive = true
                              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        """)
        org.springframework.data.domain.Page<User> searchRadiologyTechnicians(
                        @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
                        @org.springframework.data.repository.query.Param("role") String role,
                        @org.springframework.data.repository.query.Param("search") String search,
                        org.springframework.data.domain.Pageable pageable);

        /**
         * Returns the highest sequential number used by RADIOLOGY_TECHNICIAN custom IDs (RT1, RT2, …).
         * Used to derive next custom ID without a gap.
         */
        @org.springframework.data.jpa.repository.Query(
                value = "SELECT COALESCE(MAX(CAST(SUBSTRING(custom_id, 3) AS UNSIGNED)), 0) " +
                        "FROM users WHERE role = 'RADIOLOGY_TECHNICIAN' AND custom_id LIKE 'RT%'",
                nativeQuery = true)
        Integer findMaxRadiologyTechSequence();
}

