package com.hms.repository;

import com.hms.entity.RadiologyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RadiologyResultRepository extends JpaRepository<RadiologyResult, Long> {

    Optional<RadiologyResult> findByRadiologyOrderId(Long radiologyOrderId);

    boolean existsByRadiologyOrderId(Long radiologyOrderId);
}
