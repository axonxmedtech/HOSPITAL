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
    /** CLIN-P1: one query across every admission belonging to a patient, not N. */
    List<VitalsRecord> findByIpdAdmissionIdInAndIsActiveTrueOrderByRecordedAtAsc(java.util.List<Long> ipdAdmissionIds);
}
