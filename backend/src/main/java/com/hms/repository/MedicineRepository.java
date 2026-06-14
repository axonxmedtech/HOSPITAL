package com.hms.repository;

import com.hms.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    // Search for medicines matching the name, specific to this hospital
    @Query("SELECT m FROM Medicine m WHERE lower(m.name) LIKE lower(:query) AND m.hospitalId = :hospitalId AND m.isActive = true")
    List<Medicine> searchByName(String query, Long hospitalId);

    List<Medicine> findByHospitalIdAndIsActiveTrue(Long hospitalId);

    List<Medicine> findByHospitalId(Long hospitalId); // For inventory management (includes inactive)
    
    @Query("SELECT m FROM Medicine m WHERE m.hospitalId = :hospitalId AND m.stockQuantity <= m.minStockLevel AND m.isActive = true")
    List<Medicine> findLowStock(Long hospitalId);

    boolean existsByNameAndHospitalId(String name, Long hospitalId);

    java.util.Optional<Medicine> findByIdAndHospitalId(Long id, Long hospitalId);
}
