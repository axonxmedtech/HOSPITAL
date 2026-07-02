package com.hms.repository;

import com.hms.entity.InsuranceClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, Long> {

    List<InsuranceClaim> findByHospitalIdAndBillingId(Long hospitalId, Long billingId);

    Optional<InsuranceClaim> findByHospitalIdAndId(Long hospitalId, Long id);
}
