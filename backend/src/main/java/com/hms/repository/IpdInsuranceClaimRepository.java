package com.hms.repository;

import com.hms.entity.IpdInsuranceClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IpdInsuranceClaimRepository extends JpaRepository<IpdInsuranceClaim, Long> {
    List<IpdInsuranceClaim> findByHospitalIdAndIpdAdmissionIdOrderByIdDesc(Long hospitalId, Long ipdAdmissionId);
    Optional<IpdInsuranceClaim> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
    List<IpdInsuranceClaim> findByHospitalIdAndStatusInOrderByUpdatedAtDescIdDesc(Long hospitalId, Collection<String> statuses);
}
