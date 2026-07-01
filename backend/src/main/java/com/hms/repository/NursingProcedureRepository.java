package com.hms.repository;

import com.hms.entity.NursingProcedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * NursingProcedureRepository - Repository interface for NursingProcedure.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface NursingProcedureRepository extends JpaRepository<NursingProcedure, Long> {

    /**
     * Find procedures associated with progress note.
     */
    List<NursingProcedure> findByHospitalIdAndProgressNoteId(Long hospitalId, Long progressNoteId);
}
