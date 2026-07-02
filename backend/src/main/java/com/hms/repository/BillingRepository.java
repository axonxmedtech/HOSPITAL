package com.hms.repository;

import com.hms.entity.Billing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {
    Page<Billing> findByHospitalId(Long hospitalId, Pageable pageable);

    List<Billing> findByHospitalId(Long hospitalId);

    Page<Billing> findByHospitalIdAndPaymentStatus(Long hospitalId, String paymentStatus, Pageable pageable);

    @Query("""
                SELECT b FROM Billing b
                JOIN Patient p ON b.patientId = p.id
                WHERE b.hospitalId = :hospitalId
                  AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                       OR LOWER(b.customId) LIKE LOWER(CONCAT('%', :search, '%')))
                ORDER BY b.createdAt DESC
            """)
    Page<Billing> searchBillings(Long hospitalId, String search, Pageable pageable);

    java.util.Optional<Billing> findTopByPatientIdOrderByCreatedAtDesc(Long patientId);

    @Query("""
        SELECT b FROM Billing b
        WHERE b.id IN (
            SELECT MAX(b2.id)
            FROM Billing b2
            WHERE b2.patientId IN :patientIds
            GROUP BY b2.patientId
        )
    """)
    List<Billing> findLatestBillForPatients(@org.springframework.data.repository.query.Param("patientIds") List<Long> patientIds);

    List<Billing> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    java.util.List<Billing> findByIpdAdmissionId(Long ipdAdmissionId);

    boolean existsByAppointmentId(Long appointmentId);

    boolean existsByOpdId(Long opdId);

    java.util.Optional<Billing> findByAppointmentId(Long appointmentId);

    java.util.Optional<Billing> findByOpdId(Long opdId);

    java.util.List<Billing> findByHospitalIdAndCreatedAtAfter(Long hospitalId, java.time.LocalDateTime createdAt);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Billing b WHERE b.hospitalId = :hospitalId AND b.paymentStatus = :paymentStatus AND b.createdAt >= :since")
    java.math.BigDecimal sumAmountByHospitalIdAndPaymentStatusSince(
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("paymentStatus") String paymentStatus,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
