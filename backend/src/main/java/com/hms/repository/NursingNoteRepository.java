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
    List<NursingNote> findBySurgeryIdAndIsActiveTrueOrderByRecordedAtDesc(Long surgeryId);
    List<NursingNote> findBySurgeryIdAndHospitalIdAndIsActiveTrueOrderByRecordedAtDesc(Long surgeryId, Long hospitalId);
}
