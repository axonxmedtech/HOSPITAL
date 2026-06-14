package com.hms.repository;

import com.hms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Find logs ordered by timestamp descending (newest first)
    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByHospitalIdOrderByTimestampDesc(Long hospitalId);

    List<AuditLog> findByHospitalIdAndActionContainingIgnoreCaseOrderByTimestampDesc(Long hospitalId, String action);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    List<AuditLog> findByHospitalIdAndPerformedByRoleOrderByTimestampDesc(Long hospitalId, String performedByRole);

    List<AuditLog> findByHospitalIdAndPerformedByRoleAndActionContainingIgnoreCaseOrderByTimestampDesc(Long hospitalId, String performedByRole, String action);

    List<AuditLog> findByEntityTypeAndEntityIdAndHospitalIdOrderByTimestampDesc(String entityType, String entityId, Long hospitalId);

    List<AuditLog> findByEntityTypeAndHospitalIdAndActionAndEntityIdInOrderByTimestampAsc(String entityType, Long hospitalId, String action, List<String> entityIds);
}
