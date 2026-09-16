package com.hms.repository.import_;

import com.hms.entity.import_.PatientImportLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** All lookups carry the hospital: a legacy MRN or patient id from another tenant never resolves. */
@Repository
public interface PatientImportLinkRepository extends JpaRepository<PatientImportLink, Long> {

    /** The deterministic re-import match: this hospital's record for a source-system MRN. */
    Optional<PatientImportLink> findByHospitalIdAndLegacyId(Long hospitalId, String legacyId);

    Optional<PatientImportLink> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);

    /** Rows a batch CREATED — the exact set undo reverses. */
    List<PatientImportLink> findByHospitalIdAndCreatedByBatchId(Long hospitalId, Long createdByBatchId);

    long countByHospitalIdAndCreatedByBatchId(Long hospitalId, Long createdByBatchId);
}
