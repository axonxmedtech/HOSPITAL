package com.hms.repository;

import com.hms.entity.RequestIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestIdempotencyRepository extends JpaRepository<RequestIdempotency, Long> {

    Optional<RequestIdempotency> findByHospitalIdAndScopeAndIdempotencyKey(Long hospitalId, String scope,
                                                                          String idempotencyKey);
}
