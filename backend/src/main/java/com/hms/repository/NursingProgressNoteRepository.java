package com.hms.repository;

import com.hms.entity.NursingProgressNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * NursingProgressNoteRepository - Repository interface for NursingProgressNote.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface NursingProgressNoteRepository extends JpaRepository<NursingProgressNote, Long> {

    /**
     * Find progress notes for admission.
     */
    List<NursingProgressNote> findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long admissionId);

    Optional<NursingProgressNote> findByHospitalIdAndAdmissionIdAndShift(Long hospitalId, Long admissionId, String shift);

    List<NursingProgressNote> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
