package com.hms.repository;

import com.hms.entity.DoctorOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DoctorOrderRepository extends JpaRepository<DoctorOrder, Long> {
    Optional<DoctorOrder> findByPublicId(String publicId);
    Optional<DoctorOrder> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
    List<DoctorOrder> findByIpdAdmissionIdOrderByCreatedAtDesc(Long ipdAdmissionId);
    List<DoctorOrder> findByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);
    List<DoctorOrder> findByStatusAndFrequencyNot(String status, String frequency);
}
