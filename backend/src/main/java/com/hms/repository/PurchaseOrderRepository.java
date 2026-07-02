package com.hms.repository;

import com.hms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByHospitalId(Long hospitalId);
    Optional<PurchaseOrder> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<PurchaseOrder> findByHospitalIdAndPoNumber(Long hospitalId, String poNumber);
}
