package com.hms.repository;

import com.hms.entity.VitalsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VitalsRecordRepository extends JpaRepository<VitalsRecord, Long> {
    Optional<VitalsRecord> findByPublicId(String publicId);
    List<VitalsRecord> findByIpdAdmissionIdAndIsActiveTrueOrderByRecordedAtDesc(Long ipdAdmissionId);
}
