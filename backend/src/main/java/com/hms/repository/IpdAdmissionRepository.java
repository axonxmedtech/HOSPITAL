package com.hms.repository;

import com.hms.entity.IpdAdmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IpdAdmissionRepository extends JpaRepository<IpdAdmission, Long> {
    Optional<IpdAdmission> findByIpdNumber(String ipdNumber);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalId(Long hospitalId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalIdAndDoctorId(Long hospitalId, Long doctorId, org.springframework.data.domain.Pageable pageable);
    java.util.List<IpdAdmission> findByHospitalIdAndStatus(Long hospitalId, String status);
    java.util.List<IpdAdmission> findByHospitalIdAndDoctorIdAndStatus(Long hospitalId, Long doctorId, String status);
    org.springframework.data.domain.Page<IpdAdmission> findByHospitalIdAndDoctorIdAndStatus(Long hospitalId, Long doctorId, String status, org.springframework.data.domain.Pageable pageable);
    java.util.List<IpdAdmission> findByHospitalIdAndStatusIn(Long hospitalId, java.util.Collection<String> statuses);
}
