package com.hms.repository;

import com.hms.entity.WhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {
    Optional<WhatsAppConfig> findByHospitalId(Long hospitalId);
    boolean existsByHospitalId(Long hospitalId);
}
