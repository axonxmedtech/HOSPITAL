package com.hms.repository;

import com.hms.entity.NursingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NursingNoteRepository extends JpaRepository<NursingNote, Long> {
    Optional<NursingNote> findByPublicId(String publicId);
    List<NursingNote> findByIpdAdmissionIdAndIsActiveTrueOrderByRecordedAtDesc(Long ipdAdmissionId);

    @org.springframework.data.jpa.repository.Query("SELECT n FROM NursingNote n WHERE n.ipdAdmissionId = :ipdAdmissionId AND n.isActive = true " +
           "AND (:category IS NULL OR n.category = :category OR (:category = 'NURSE' AND n.category IS NULL)) " +
           "ORDER BY n.recordedAt DESC")
    List<NursingNote> findByAdmissionAndCategory(@org.springframework.data.repository.query.Param("ipdAdmissionId") Long ipdAdmissionId, @org.springframework.data.repository.query.Param("category") String category);

    /** CLIN-P1: one query across every admission belonging to a patient, not N. */
    List<NursingNote> findByIpdAdmissionIdInAndIsActiveTrueOrderByRecordedAtAsc(java.util.List<Long> ipdAdmissionIds);
    List<NursingNote> findBySurgeryIdAndIsActiveTrueOrderByRecordedAtDesc(Long surgeryId);
    List<NursingNote> findBySurgeryIdAndHospitalIdAndIsActiveTrueOrderByRecordedAtDesc(Long surgeryId, Long hospitalId);
}
