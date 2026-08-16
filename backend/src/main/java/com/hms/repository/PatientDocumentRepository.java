package com.hms.repository;

import com.hms.entity.PatientDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    List<PatientDocument> findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(
            Long hospitalId, Long patientId);

    Optional<PatientDocument> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
}
