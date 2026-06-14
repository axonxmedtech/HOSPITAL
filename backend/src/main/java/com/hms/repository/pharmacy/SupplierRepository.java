package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Page<Supplier> findByHospitalIdAndSupplierNameContainingIgnoreCase(Long hospitalId, String name, Pageable pageable);
    Page<Supplier> findByHospitalId(Long hospitalId, Pageable pageable);
    Optional<Supplier> findByIdAndHospitalId(Long id, Long hospitalId);
}
