package com.hms.repository;

import com.hms.entity.NurseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NurseTaskRepository extends JpaRepository<NurseTask, Long> {
    List<NurseTask> findByIpdAdmissionIdOrderByScheduledAtDesc(Long ipdAdmissionId);
    List<NurseTask> findByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);
    List<NurseTask> findByDoctorOrderIdAndStatus(Long doctorOrderId, String status);
    List<NurseTask> findByIpdAdmissionIdInAndStatus(List<Long> ipdAdmissionIds, String status);
    boolean existsByDoctorOrderIdAndStatus(Long doctorOrderId, String status);
}
