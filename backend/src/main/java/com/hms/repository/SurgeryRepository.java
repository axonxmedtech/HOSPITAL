package com.hms.repository;

import com.hms.entity.Surgery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurgeryRepository extends JpaRepository<Surgery, Long> {
    Optional<Surgery> findByPublicId(String publicId);

    List<Surgery> findByHospitalIdAndStatusOrderByRequestedAtDesc(Long hospitalId, String status);

    List<Surgery> findByHospitalIdAndStatusInOrderByScheduledAtAsc(Long hospitalId, Collection<String> statuses);

    List<Surgery> findByIpdAdmissionIdOrderByRequestedAtDesc(Long ipdAdmissionId);

    List<Surgery> findByIpdAdmissionIdAndStatusIn(Long ipdAdmissionId, Collection<String> statuses);

    List<Surgery> findByOtWardIdAndStatus(Long otWardId, String status);
}
