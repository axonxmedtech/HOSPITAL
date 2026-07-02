package com.hms.repository;

import com.hms.entity.WasteCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WasteCollectionRepository extends JpaRepository<WasteCollection, Long> {
    List<WasteCollection> findByHospitalIdOrderByCollectionTimeDesc(Long hospitalId);
}
