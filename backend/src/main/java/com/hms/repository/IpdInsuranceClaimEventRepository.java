package com.hms.repository;

import com.hms.entity.IpdInsuranceClaimEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IpdInsuranceClaimEventRepository extends JpaRepository<IpdInsuranceClaimEvent, Long> {
    List<IpdInsuranceClaimEvent> findByHospitalIdAndClaimIdOrderByIdAsc(Long hospitalId, Long claimId);
}
