package com.hms.repository;

import com.hms.entity.LabOrder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/**
 * LabOrderRepository — Extended to support the full lab workflow.
 * Provides scoped queries for: dashboard (paginated by status),
 * IPD admission view (list all orders for one admission),
 * patient history, and single-order fetch with hospital scope check.
 */
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    /** Used for single-order fetch; hospital scope enforced here */
    Optional<LabOrder> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    /** Legacy OPD flow — kept for backward compatibility */
    List<LabOrder> findByMedicalRecordId(Long medicalRecordId);

    /** Dashboard: all orders for this hospital, newest first */
    Page<LabOrder> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);

    /** Dashboard filtered by status (ORDERED / SAMPLE_COLLECTED / COMPLETED / CANCELLED) */
    Page<LabOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, Pageable pageable);

    /** IPD Details: all orders for a single admission */
    List<LabOrder> findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long ipdAdmissionId);

    /** Patient history across admissions */
    List<LabOrder> findByHospitalIdAndPatientIdOrderByCreatedAtDesc(Long hospitalId, Long patientId);

    /** IPD Details filtered by status (e.g. only ORDERED pending orders) */
    List<LabOrder> findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(
            Long hospitalId, Long ipdAdmissionId, String status);
}
