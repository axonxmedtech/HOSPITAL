package com.hms.repository;

import com.hms.entity.MrdRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MrdRecordRepository extends JpaRepository<MrdRecord, Long> {

    Optional<MrdRecord> findByIpdAdmissionIdAndHospitalId(Long ipdAdmissionId, Long hospitalId);

    Optional<MrdRecord> findByIpdAdmissionId(Long ipdAdmissionId);

    java.util.List<MrdRecord> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

    @Query("SELECT MAX(CAST(SUBSTRING(m.mrdNumber, 5) AS int)) FROM MrdRecord m")
    Integer findMaxMrdSequence();
}
