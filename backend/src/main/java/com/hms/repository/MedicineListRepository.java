package com.hms.repository;

import com.hms.entity.MedicineList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicineListRepository extends JpaRepository<MedicineList, Long> {

    @Query("SELECT m FROM MedicineList m WHERE lower(m.name) LIKE lower(:query) AND (m.hospitalId IS NULL OR m.hospitalId = :hospitalId) AND m.isActive = true")
    List<MedicineList> searchByName(String query, Long hospitalId);

    List<MedicineList> findByHospitalIdAndIsActiveTrue(Long hospitalId);

    List<MedicineList> findByHospitalId(Long hospitalId);

    boolean existsByNameAndHospitalId(String name, Long hospitalId);
}
