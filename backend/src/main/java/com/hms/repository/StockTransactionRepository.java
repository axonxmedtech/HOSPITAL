package com.hms.repository;

import com.hms.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findByHospitalId(Long hospitalId);
    List<StockTransaction> findByHospitalIdOrderByTransactionTimeDesc(Long hospitalId);
}
