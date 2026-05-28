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

    java.util.List<Billing> findByIpdAdmissionId(Long ipdAdmissionId);

    boolean existsByAppointmentId(Long appointmentId);

    boolean existsByOpdId(Long opdId);

    java.util.Optional<Billing> findByAppointmentId(Long appointmentId);

    java.util.Optional<Billing> findByOpdId(Long opdId);
}
