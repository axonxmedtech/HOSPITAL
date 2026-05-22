package com.hms.repository;

import com.hms.entity.Opd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpdRepository extends JpaRepository<Opd, Long> {

	Page<Opd> findByPatient_HospitalId(Long hospitalId, Pageable pageable);

	@Query("SELECT o FROM Opd o LEFT JOIN o.doctor d WHERE o.patient.hospitalId = :hospitalId " +
			"AND (:search IS NULL OR LOWER(o.caseId) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(o.patient.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
			"OR LOWER(d.name) LIKE LOWER(CONCAT('%',:search,'%'))) " +
			"AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
			"AND (:endDate IS NULL OR o.createdAt <= :endDate)")
	Page<Opd> searchByHospitalAndDateRange(
			@Param("hospitalId") Long hospitalId, 
			@Param("search") String search, 
			@Param("startDate") java.time.LocalDateTime startDate, 
			@Param("endDate") java.time.LocalDateTime endDate, 
			Pageable pageable);

	boolean existsByPatientIdAndVisitTypeAndCreatedAtGreaterThanEqual(
			Long patientId, 
			com.hms.entity.Opd.VisitType visitType, 
			java.time.LocalDateTime startOfDay
	);
}
