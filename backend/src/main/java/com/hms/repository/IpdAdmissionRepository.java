package com.hms.repository;

import com.hms.entity.IpdAdmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface IpdAdmissionRepository extends JpaRepository<IpdAdmission, Long> {
    Optional<IpdAdmission> findByIpdNumber(String ipdNumber);

    /**
     * Portable across MySQL (production) and H2 (test profile). MySQL's CAST accepts
     * SIGNED/UNSIGNED but not INT/BIGINT; H2 accepts INT/BIGINT but not UNSIGNED.
     * DECIMAL(n,0) is the one integer-valued cast target both accept, and it preserves
     * the original numeric (not lexicographic) MAX semantics. Precision 20 is far above
     * any realistic sequence and keeps the value exact.
     */
    @org.springframework.data.jpa.repository.Query(
        value = "SELECT COALESCE(MAX(CAST(SUBSTRING(ipd_number, 5) AS DECIMAL(20,0))), 0) FROM ipd_admission WHERE ipd_number LIKE 'IPD-%'",
        nativeQuery = true)
    Long findMaxIpdSequence();
    boolean existsByHospitalIdAndPatientIdAndStatusIn(Long hospitalId, Long patientId,
            java.util.Collection<String> statuses);

    Optional<IpdAdmission> findByIdAndHospitalId(Long id, Long hospitalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM IpdAdmission i WHERE i.id = :id AND i.hospitalId = :hospitalId")
    Optional<IpdAdmission> findByIdAndHospitalIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalId(Long hospitalId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalIdAndDoctorId(Long hospitalId, Long doctorId, org.springframework.data.domain.Pageable pageable);
    java.util.List<IpdAdmission> findByHospitalIdAndStatus(Long hospitalId, String status);
    java.util.List<IpdAdmission> findByHospitalIdAndDoctorIdAndStatus(Long hospitalId, Long doctorId, String status);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalIdAndDoctorIdAndStatus(Long hospitalId, Long doctorId, String status, org.springframework.data.domain.Pageable pageable);
    java.util.List<IpdAdmission> findByPatientIdOrderByAdmissionDatetimeDesc(Long patientId);
    java.util.List<IpdAdmission> findByHospitalIdAndStatusIn(Long hospitalId, java.util.Collection<String> statuses);
    java.util.List<IpdAdmission> findByHospitalIdAndWardIdInAndStatusIn(Long hospitalId,
            java.util.Collection<Long> wardIds, java.util.Collection<String> statuses);
    java.util.List<IpdAdmission> findByHospitalIdAndAdmissionDatetimeBetween(Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    /**
     * ICU Phase 2 — active admissions inside a set of wards. Replaces the existing pattern of
     * loading every admission for the hospital and filtering by ward in Java, which the ICU
     * board would otherwise repeat on every poll.
     */
    java.util.List<IpdAdmission> findByHospitalIdAndStatusInAndWardIdIn(
            Long hospitalId, java.util.Collection<String> statuses, java.util.Collection<Long> wardIds);
}
