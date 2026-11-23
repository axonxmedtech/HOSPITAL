package com.hms.repository;

import com.hms.entity.DietOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DietOrderRepository extends JpaRepository<DietOrder, Long> {
    List<DietOrder> findByHospitalIdAndIpdAdmissionIdOrderByStartedAtDescIdDesc(Long hospitalId, Long ipdAdmissionId);
    Optional<DietOrder> findFirstByHospitalIdAndIpdAdmissionIdAndEndedAtIsNullOrderByIdDesc(Long hospitalId, Long ipdAdmissionId);
    List<DietOrder> findByHospitalIdAndIpdAdmissionIdInAndEndedAtIsNull(Long hospitalId, Collection<Long> ipdAdmissionIds);
}
