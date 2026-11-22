package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.PharmacyInvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PharmacyInvoiceSequenceRepository extends JpaRepository<PharmacyInvoiceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PharmacyInvoiceSequence s WHERE s.hospitalId = :hospitalId AND s.series = :series")
    Optional<PharmacyInvoiceSequence> lockSeries(@Param("hospitalId") Long hospitalId, @Param("series") String series);

    /** Plain read, no lock: on MySQL a locking read of a missing row would lock the gap it belongs in. */
    boolean existsByHospitalIdAndSeries(Long hospitalId, String series);
}
