package com.hms.repository;

import com.hms.entity.PatientAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Patient access log, plus the hospital-scoped lookups that trace a requested record back to its
 * patient. Every lookup carries the hospital id, so a record of another facility resolves to
 * nothing.
 */
public interface PatientAccessLogRepository extends JpaRepository<PatientAccessLog, Long> {

    Page<PatientAccessLog> findByHospitalIdAndPatientIdOrderByAccessedAtDesc(
            Long hospitalId, Long patientId, Pageable pageable);

    /** Was the same access already recorded a moment ago? (a re-render or a quick re-open) */
    @Query("SELECT COUNT(l) > 0 FROM PatientAccessLog l WHERE l.hospitalId = :hospitalId "
            + "AND l.userEmail = :userEmail AND l.recordType = :recordType AND l.action = :action "
            + "AND ((:patientId IS NULL AND l.patientId IS NULL) OR l.patientId = :patientId) "
            + "AND ((:recordRef IS NULL AND l.recordRef IS NULL) OR l.recordRef = :recordRef) "
            + "AND l.accessedAt >= :since")
    boolean existsRecent(@Param("hospitalId") Long hospitalId, @Param("patientId") Long patientId,
            @Param("userEmail") String userEmail, @Param("recordType") String recordType,
            @Param("recordRef") String recordRef, @Param("action") String action,
            @Param("since") LocalDateTime since);

    // ── record → patient (all hospital-scoped) ──────────────────────────────────────────────

    @Query("SELECT p.id FROM Patient p WHERE p.publicId = :ref AND p.hospitalId = :hospitalId")
    List<Long> patientByPublicId(@Param("ref") String ref, @Param("hospitalId") Long hospitalId);

    @Query("SELECT p.id FROM Patient p WHERE p.id = :id AND p.hospitalId = :hospitalId")
    List<Long> patientByPk(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT o.patient.id FROM Opd o WHERE o.id = :id AND o.patient.hospitalId = :hospitalId")
    List<Long> patientOfOpd(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT a.patientId FROM IpdAdmission a WHERE a.id = :id AND a.hospitalId = :hospitalId")
    List<Long> patientOfAdmission(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT a.patientId FROM Appointment a WHERE a.publicId = :ref AND a.hospitalId = :hospitalId")
    List<Long> patientOfAppointmentPublicId(@Param("ref") String ref, @Param("hospitalId") Long hospitalId);

    @Query("SELECT a.patientId FROM Appointment a WHERE a.id = :id AND a.hospitalId = :hospitalId")
    List<Long> patientOfAppointmentPk(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT b.patientId FROM Billing b WHERE b.id = :id AND b.hospitalId = :hospitalId")
    List<Long> patientOfBill(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT b.patientId FROM BillingPayment p, Billing b "
            + "WHERE p.id = :id AND b.id = p.billingId AND b.hospitalId = :hospitalId")
    List<Long> patientOfPayment(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    @Query("SELECT d.patientId FROM PatientDocument d WHERE d.publicId = :ref AND d.hospitalId = :hospitalId")
    List<Long> patientOfDocument(@Param("ref") String ref, @Param("hospitalId") Long hospitalId);

    @Query("SELECT s.patientId FROM IcuStay s WHERE s.publicId = :ref AND s.hospitalId = :hospitalId")
    List<Long> patientOfIcuStay(@Param("ref") String ref, @Param("hospitalId") Long hospitalId);
}
