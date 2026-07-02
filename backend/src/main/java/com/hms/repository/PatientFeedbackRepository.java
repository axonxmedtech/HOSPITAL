package com.hms.repository;

import com.hms.entity.PatientFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientFeedbackRepository extends JpaRepository<PatientFeedback, Long> {
    Optional<PatientFeedback> findByIdAndHospitalId(Long id, Long hospitalId);

    List<PatientFeedback> findByHospitalIdOrderBySubmittedAtDesc(Long hospitalId);

    boolean existsByHospitalIdAndAppointmentId(Long hospitalId, Long appointmentId);

    boolean existsByHospitalIdAndAdmissionId(Long hospitalId, Long admissionId);
}
