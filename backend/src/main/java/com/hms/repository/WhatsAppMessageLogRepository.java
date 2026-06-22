package com.hms.repository;

import com.hms.entity.WhatsAppMessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLog, Long> {

    Page<WhatsAppMessageLog> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndMessageTypeOrderByCreatedAtDesc(Long hospitalId, String messageType, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndMessageTypeAndStatusOrderByCreatedAtDesc(
            Long hospitalId, String messageType, String status, Pageable pageable);

    long countByHospitalIdAndStatus(Long hospitalId, String status);

    List<WhatsAppMessageLog> findByStatusAndNextRetryAtBeforeAndRetryCountLessThan(
            String status, LocalDateTime now, int maxRetries);

    @Query("SELECT COUNT(l) FROM WhatsAppMessageLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countByStatusSince(@Param("status") String status, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT l.hospitalId) FROM WhatsAppMessageLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countDistinctHospitalsByStatusSince(@Param("status") String status, @Param("since") LocalDateTime since);
}
