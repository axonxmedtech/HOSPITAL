package com.hms.repository;

import com.hms.entity.PurchaseRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {
    List<PurchaseRequisition> findByHospitalId(Long hospitalId);
    Optional<PurchaseRequisition> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<PurchaseRequisition> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
}
