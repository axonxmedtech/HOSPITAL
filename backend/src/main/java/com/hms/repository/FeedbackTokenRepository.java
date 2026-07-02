package com.hms.repository;

import com.hms.entity.FeedbackToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackTokenRepository extends JpaRepository<FeedbackToken, Long> {
    Optional<FeedbackToken> findByToken(String token);

    boolean existsByHospitalIdAndAppointmentId(Long hospitalId, Long appointmentId);

    boolean existsByHospitalIdAndAdmissionId(Long hospitalId, Long admissionId);
}
