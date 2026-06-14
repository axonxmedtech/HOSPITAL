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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT b FROM MedicineBatch b WHERE b.id = :id AND b.hospitalId = :hospitalId")
    java.util.Optional<MedicineBatch> findByIdAndHospitalIdForUpdate(Long id, Long hospitalId);

    Page<MedicineBatch> findByHospitalId(Long hospitalId, Pageable pageable);
    
    Page<MedicineBatch> findByHospitalIdAndMedicine_CategoryId(Long hospitalId, Long categoryId, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b JOIN b.medicine m WHERE b.hospitalId = :hospitalId AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<MedicineBatch> searchInventory(@Param("hospitalId") Long hospitalId, @Param("q") String query, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b JOIN b.medicine m WHERE b.hospitalId = :hospitalId AND m.categoryId = :catId AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<MedicineBatch> searchInventoryWithCategory(@Param("hospitalId") Long hospitalId, @Param("q") String query, @Param("catId") Long categoryId, Pageable pageable);

    @Query("SELECT b FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.expiryDate <= :dateThreshold AND b.currentQuantity > 0")
    Page<MedicineBatch> findExpiringSoon(@Param("hospitalId") Long hospitalId, @Param("dateThreshold") LocalDate dateThreshold, Pageable pageable);

    @Query("SELECT SUM(b.currentQuantity) FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.medicineId = :medicineId")
    java.math.BigDecimal sumCurrentQuantityByMedicineId(@Param("hospitalId") Long hospitalId, @Param("medicineId") Long medicineId);

    @Query("SELECT b FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.id IN (" +
           "  SELECT MIN(sub.id) FROM MedicineBatch sub " +
           "  WHERE sub.hospitalId = :hospitalId " +
           "  GROUP BY sub.medicineId " +
           "  HAVING SUM(sub.currentQuantity) <= (SELECT m.reorderLevel FROM MedicineMaster m WHERE m.id = sub.medicineId)" +
           ")")
    Page<MedicineBatch> findLowStock(@Param("hospitalId") Long hospitalId, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.medicineId = :medicineId AND b.batchNumber = :batchNumber")
    java.util.Optional<MedicineBatch> findByHospitalIdAndMedicineIdAndBatchNumberForUpdate(
            @Param("hospitalId") Long hospitalId, 
            @Param("medicineId") Long medicineId, 
            @Param("batchNumber") String batchNumber);

    @Query("SELECT b FROM MedicineBatch b JOIN b.medicine m WHERE b.hospitalId = :hospitalId AND b.currentQuantity > 0 " +
           "AND b.expiryDate > CURRENT_DATE " +
           "AND (b.status IS NULL OR b.status = 'ACTIVE') AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.batchNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY b.expiryDate ASC")
    java.util.List<MedicineBatch> searchAvailableBatchesFEFO(@Param("hospitalId") Long hospitalId, @Param("q") String query);

    java.util.Optional<MedicineBatch> findByHospitalIdAndMedicineIdAndBatchNumber(Long hospitalId, Long medicineId, String batchNumber);
}
