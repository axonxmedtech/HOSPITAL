package com.hms.repository;

import com.hms.entity.IcuStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ICU Phase 3. Every finder is tenant-scoped; nothing here resolves a bare id. */
public interface IcuStayRepository extends JpaRepository<IcuStay, Long> {
    boolean existsByWardIdAndHospitalId(Long wardId, Long hospitalId);


    Optional<IcuStay> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    /** Patient File: every ICU stay of one patient, newest first. */
    List<IcuStay> findByPatientIdAndHospitalIdOrderByAdmittedAtDesc(Long patientId, Long hospitalId);

    Optional<IcuStay> findByIpdAdmissionIdAndHospitalIdAndStatus(
            Long ipdAdmissionId, Long hospitalId, String status);

    List<IcuStay> findByIpdAdmissionIdAndHospitalIdOrderByAdmittedAtDesc(
            Long ipdAdmissionId, Long hospitalId);

    /** Active stays for a set of admissions — the ICU board's batched read. */
    List<IcuStay> findByHospitalIdAndStatusAndIpdAdmissionIdIn(
            Long hospitalId, String status, Collection<Long> ipdAdmissionIds);

    /**
     * Tenant-scoped by construction. The one caller passes an already-verified admission, but an
     * unscoped existence primitive sitting in the repository is an invitation for the next caller
     * to use it without that guarantee.
     */
    boolean existsByIpdAdmissionIdAndHospitalIdAndStatus(
            Long ipdAdmissionId, Long hospitalId, String status);

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

    /**
     * ICU Phase 7 — WHICH stay covered this instant, for the provenance stamp (D-2).
     *
     * <p>Same predicate as {@link #existsCoveringInstant}, returning the row instead of a boolean.
     * Read-only: ICU-7 stamps the id and never touches the stay lifecycle. Newest first, so
     * overlapping historical rows resolve to the most recent.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM IcuStay s WHERE s.ipdAdmissionId = :admissionId "
      + "AND s.hospitalId = :hospitalId AND s.admittedAt <= :at "
      + "AND (s.dischargedAt IS NULL OR s.dischargedAt >= :at) "
      + "ORDER BY s.admittedAt DESC, s.id DESC")
    List<IcuStay> findCoveringInstant(
            @org.springframework.data.repository.query.Param("admissionId") Long admissionId,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("at") java.time.LocalDateTime at);

    /**
     * ICU history: stays by status within a set of units, optional doctor (the admission's
     * doctor or the intensivist), a date window (on the ICU discharge date for the Closed view,
     * on the ICU admission date otherwise) and a search over patient name / phone / id and IPD
     * number. Tenant-scoped on every table it touches.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT s FROM IcuStay s
        WHERE s.hospitalId = :hospitalId
          AND s.status IN :statuses
          AND s.wardId IN :wardIds
          AND (:doctorId IS NULL OR s.intensivistDoctorId = :doctorId
               OR s.ipdAdmissionId IN (SELECT a.id FROM IpdAdmission a WHERE a.hospitalId = :hospitalId AND a.doctorId = :doctorId))
          AND (:from IS NULL OR (CASE WHEN :byDischarge = true THEN s.dischargedAt ELSE s.admittedAt END) >= :from)
          AND (:to IS NULL OR (CASE WHEN :byDischarge = true THEN s.dischargedAt ELSE s.admittedAt END) < :to)
          AND (:q IS NULL
               OR s.patientId IN (SELECT p.id FROM Patient p WHERE p.hospitalId = :hospitalId
                                  AND (LOWER(p.name) LIKE :q OR p.phone LIKE :q OR LOWER(p.customId) LIKE :q))
               OR s.ipdAdmissionId IN (SELECT a2.id FROM IpdAdmission a2 WHERE a2.hospitalId = :hospitalId AND LOWER(a2.ipdNumber) LIKE :q))
        """)
    org.springframework.data.domain.Page<IcuStay> searchHistory(
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("statuses") Collection<String> statuses,
            @org.springframework.data.repository.query.Param("wardIds") Collection<Long> wardIds,
            @org.springframework.data.repository.query.Param("doctorId") Long doctorId,
            @org.springframework.data.repository.query.Param("byDischarge") boolean byDischarge,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
