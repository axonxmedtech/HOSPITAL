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
	 * The only way to load a single OPD.
	 *
	 * <p>Opd has no hospital_id of its own, so tenancy is proven through the owning patient. The
	 * WHERE on p.hospitalId makes the patient join effectively inner: an OPD whose patient cannot
	 * be tenant-verified is never returned. A cross-tenant id therefore yields empty, which
	 * callers surface as 404.
	 *
	 * <p>It also eagerly fetches patient and doctor, so callers can read their fields after the
	 * transaction closes -- with spring.jpa.open-in-view=false a plain findById() leaves them as
	 * lazy proxies that throw once touched, which is why PDF generation needs this shape.
	 *
	 * <p>An unscoped twin of this query used to sit directly above it. Because Opd carries no
	 * hospital_id, the usual fallback of loading by id and comparing entity.getHospitalId()
	 * afterwards is not available here -- there is nothing to compare. The scoped query is the only
	 * safe way to fetch one OPD, so the unscoped one was removed rather than left as something to
	 * reach for by mistake. OpdRepositoryScopingArchTest keeps it gone.
	 */
	@Query("SELECT o FROM Opd o LEFT JOIN FETCH o.patient p LEFT JOIN FETCH o.doctor "
			+ "WHERE o.id = :id AND p.hospitalId = :hospitalId")
	Optional<Opd> findByIdAndHospitalIdWithPatientAndDoctor(@Param("id") Long id,
			@Param("hospitalId") Long hospitalId);

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
