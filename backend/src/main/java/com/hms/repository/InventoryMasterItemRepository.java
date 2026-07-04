package com.hms.repository;

import com.hms.entity.InventoryMasterItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryMasterItemRepository extends JpaRepository<InventoryMasterItem, Long> {
    List<InventoryMasterItem> findAllByOrderByNameAsc();
    Optional<InventoryMasterItem> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
