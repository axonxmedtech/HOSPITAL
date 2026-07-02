package com.hms.repository;

import com.hms.entity.CrossMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrossMatchRepository extends JpaRepository<CrossMatch, Long> {
    Optional<CrossMatch> findTopByHospitalIdAndBloodUnitIdAndPatientIdOrderByVerifiedAtDesc(
            Long hospitalId, Long bloodUnitId, Long patientId);

    List<CrossMatch> findByHospitalIdAndRequestId(Long hospitalId, Long requestId);
}
