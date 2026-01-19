package com.hms.repository;

import com.hms.entity.Billing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {
    Page<Billing> findByHospitalId(Long hospitalId, Pageable pageable);

    List<Billing> findByHospitalId(Long hospitalId);

    Page<Billing> findByHospitalIdAndPaymentStatus(Long hospitalId, String paymentStatus, Pageable pageable);

    java.util.Optional<Billing> findTopByPatientIdOrderByCreatedAtDesc(Long patientId);
}
