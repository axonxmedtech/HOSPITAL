package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.MedicineCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicineCategoryRepository extends JpaRepository<MedicineCategory, Long> {
    
    Page<MedicineCategory> findByHospitalIdAndCategoryNameContainingIgnoreCase(Long hospitalId, String name, Pageable pageable);
    
    Page<MedicineCategory> findByHospitalId(Long hospitalId, Pageable pageable);
    
    Optional<MedicineCategory> findByIdAndHospitalId(Long id, Long hospitalId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) > 0 FROM MedicineCategory c WHERE c.hospitalId = :hid "
            + "AND LOWER(TRIM(c.categoryName)) = LOWER(TRIM(:name)) AND (:exceptId IS NULL OR c.id <> :exceptId)")
    boolean nameTaken(@org.springframework.data.repository.query.Param("hid") Long hid,
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("exceptId") Long exceptId);
}
