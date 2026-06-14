package com.hms.repository;

import com.hms.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByHospitalId(Long hospitalId);

    boolean existsByNameAndHospitalId(String name, Long hospitalId);

    Optional<InventoryItem> findByNameAndHospitalId(String name, Long hospitalId);

    Optional<InventoryItem> findByIdAndHospitalId(Long id, Long hospitalId);

    @Query("SELECT i FROM InventoryItem i WHERE i.hospitalId = :hospitalId AND LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<InventoryItem> searchByName(@Param("query") String query, @Param("hospitalId") Long hospitalId);
}
