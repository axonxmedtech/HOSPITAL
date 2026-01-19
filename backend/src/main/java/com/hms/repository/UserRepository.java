package com.hms.repository;

import com.hms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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

        /**
         * Find users by hospital ID and role (Active or Inactive)
         */
        java.util.List<User> findByHospitalIdAndRole(Long hospitalId, String role);

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
}
