package com.hms.repository;

import com.hms.entity.Opd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OpdRepository extends JpaRepository<Opd, Long> {

	Page<Opd> findByPatient_HospitalId(Long hospitalId, Pageable pageable);

	/**
	 * Eagerly fetches patient/doctor so callers can safely read their fields
	 * (e.g. PDF generation) after this method returns, even with
	 * spring.jpa.open-in-view=false closing the session at the transaction
	 * boundary -- a plain findById() leaves patient/doctor as lazy proxies
	 * that throw LazyInitializationException once touched outside that
	 * transaction.
	 */
	@Query("SELECT o FROM Opd o LEFT JOIN FETCH o.patient LEFT JOIN FETCH o.doctor WHERE o.id = :id")
	Optional<Opd> findByIdWithPatientAndDoctor(@Param("id") Long id);

	@Query(value = "SELECT DISTINCT o FROM Opd o " +
			"INNER JOIN FETCH o.patient p " +
			"LEFT JOIN FETCH o.doctor d " +
			"LEFT JOIN FETCH o.receptionist " +
			"WHERE p.hospitalId = :hospitalId " +
			"AND (:status IS NULL OR o.status = :status) " +
			"AND (:search IS NULL OR LOWER(o.caseId) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(p.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(d.name) LIKE LOWER(CONCAT('%',:search,'%'))) " +
			"AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
			"AND (:endDate IS NULL OR o.createdAt <= :endDate)",
		countQuery = "SELECT COUNT(DISTINCT o) FROM Opd o " +
			"INNER JOIN o.patient p " +
			"LEFT JOIN o.doctor d " +
			"WHERE p.hospitalId = :hospitalId " +
			"AND (:status IS NULL OR o.status = :status) " +
			"AND (:search IS NULL OR LOWER(o.caseId) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(p.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(d.name) LIKE LOWER(CONCAT('%',:search,'%'))) " +
			"AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
			"AND (:endDate IS NULL OR o.createdAt <= :endDate)")
	Page<Opd> searchByHospitalAndDateRange(
			@Param("hospitalId") Long hospitalId,
			@Param("search") String search,
			@Param("startDate") java.time.LocalDateTime startDate,
			@Param("endDate") java.time.LocalDateTime endDate,
			@Param("status") com.hms.entity.Opd.Status status,
			Pageable pageable);

	boolean existsByPatientIdAndVisitTypeAndCreatedAtGreaterThanEqual(
			Long patientId, 
			com.hms.entity.Opd.VisitType visitType, 
			java.time.LocalDateTime startOfDay
	);
}
