package com.hms.repository;

import com.hms.entity.MedicinePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MedicinePurchaseRepository extends JpaRepository<MedicinePurchase, Long> {
    List<MedicinePurchase> findByHospitalIdOrderByPurchaseDateDesc(Long hospitalId);
    /** Newest purchases first, at most one page (the screens' list). */
    java.util.List<MedicinePurchase> findByHospitalIdOrderByPurchaseDateDesc(Long hospitalId, org.springframework.data.domain.Pageable limit);
}
