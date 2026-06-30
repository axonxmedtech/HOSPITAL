package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.MedicineMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineMasterRepository extends JpaRepository<MedicineMaster, Long> {
    
    Optional<MedicineMaster> findByIdAndHospitalId(Long id, Long hospitalId);
    
    Page<MedicineMaster> findByHospitalId(Long hospitalId, Pageable pageable);

    @Query("SELECT m FROM MedicineMaster m WHERE m.hospitalId = :hospitalId AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.genericName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.medicineCode) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<MedicineMaster> searchMedicines(@Param("hospitalId") Long hospitalId, @Param("q") String query, Pageable pageable);

    @Query("SELECT m FROM MedicineMaster m WHERE m.hospitalId = :hospitalId AND (" +
           "LOWER(m.medicineName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.genericName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.medicineCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND m.isActive = true")
    List<MedicineMaster> findTop10ByHospitalIdAndMedicineNameContainingIgnoreCase(@Param("hospitalId") Long hospitalId, @Param("q") String query);

    List<MedicineMaster> findByHospitalIdAndIsActiveTrueOrderByMedicineNameAsc(Long hospitalId);

    @Query("SELECT m FROM MedicineMaster m WHERE m.hospitalId = :hospitalId AND m.isActive = true " +
           "AND LOWER(m.medicineName) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<MedicineMaster> searchByHospitalAndName(@Param("hospitalId") Long hospitalId, @Param("q") String q);
}
