package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.MedicineBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    Page<MedicineBatch> findByHospitalId(Long hospitalId, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b JOIN b.medicine m WHERE b.hospitalId = :hospitalId AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<MedicineBatch> searchInventory(@Param("hospitalId") Long hospitalId, @Param("q") String query, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.expiryDate <= :dateThreshold AND b.currentQuantity > 0")
    Page<MedicineBatch> findExpiringSoon(@Param("hospitalId") Long hospitalId, @Param("dateThreshold") LocalDate dateThreshold, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.currentQuantity <= b.medicine.reorderLevel AND b.currentQuantity > 0")
    Page<MedicineBatch> findLowStock(@Param("hospitalId") Long hospitalId, Pageable pageable);
}
