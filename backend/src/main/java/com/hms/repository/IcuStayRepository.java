package com.hms.repository;

import com.hms.entity.IcuStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ICU Phase 3. Every finder is tenant-scoped; nothing here resolves a bare id. */
public interface IcuStayRepository extends JpaRepository<IcuStay, Long> {

    Optional<IcuStay> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    Optional<IcuStay> findByIpdAdmissionIdAndHospitalIdAndStatus(
            Long ipdAdmissionId, Long hospitalId, String status);

    List<IcuStay> findByIpdAdmissionIdAndHospitalIdOrderByAdmittedAtDesc(
            Long ipdAdmissionId, Long hospitalId);

    /** Active stays for a set of admissions — the ICU board's batched read. */
    List<IcuStay> findByHospitalIdAndStatusAndIpdAdmissionIdIn(
            Long hospitalId, String status, Collection<Long> ipdAdmissionIds);

    boolean existsByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);

    /**
     * ICU Phase 4 — was this instant inside an ICU stay for this admission?
     *
     * <p>A closed stay is bounded by discharged_at; the active one is open-ended. Read-only:
     * ICU-4 records observations and never touches the stay lifecycle.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(s) > 0 FROM IcuStay s WHERE s.ipdAdmissionId = :admissionId "
      + "AND s.hospitalId = :hospitalId AND s.admittedAt <= :at "
      + "AND (s.dischargedAt IS NULL OR s.dischargedAt >= :at)")
    boolean existsCoveringInstant(
            @org.springframework.data.repository.query.Param("admissionId") Long admissionId,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("at") java.time.LocalDateTime at);
}
