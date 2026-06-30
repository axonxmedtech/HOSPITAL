package com.hms.repository;

import com.hms.entity.RadiologyOrder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface RadiologyOrderRepository extends JpaRepository<RadiologyOrder, Long> {

    Optional<RadiologyOrder> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    List<RadiologyOrder> findByMedicalRecordId(Long medicalRecordId);

    Page<RadiologyOrder> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);

    Page<RadiologyOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, Pageable pageable);

    List<RadiologyOrder> findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long ipdAdmissionId);

    List<RadiologyOrder> findByHospitalIdAndPatientIdOrderByCreatedAtDesc(Long hospitalId, Long patientId);

    List<RadiologyOrder> findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(
            Long hospitalId, Long ipdAdmissionId, String status);

    long countByHospitalIdAndStatus(Long hospitalId, String status);
}
