package com.hms.repository;

import com.hms.entity.ConsultationNotePreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationNotePresetRepository extends JpaRepository<ConsultationNotePreset, Long> {
    List<ConsultationNotePreset> findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(Long hospitalId, String fieldType);
    Optional<ConsultationNotePreset> findByIdAndHospitalId(Long id, Long hospitalId);
}
