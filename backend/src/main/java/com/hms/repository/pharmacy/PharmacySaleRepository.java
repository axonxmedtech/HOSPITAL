package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.PharmacySale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PharmacySaleRepository extends JpaRepository<PharmacySale, Long> {
    Page<PharmacySale> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);
    Optional<PharmacySale> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<PharmacySale> findByBillNumberAndHospitalId(String billNumber, Long hospitalId);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(s.netAmount) FROM PharmacySale s WHERE s.hospitalId = :hospitalId AND s.createdAt BETWEEN :start AND :end")
    java.math.BigDecimal getSumOfSalesBetween(Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByHospitalIdAndCreatedAtBetween(Long hospitalId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    java.util.List<PharmacySale> findByHospitalIdAndCreatedAtAfter(Long hospitalId, java.time.LocalDateTime createdAt);
}
