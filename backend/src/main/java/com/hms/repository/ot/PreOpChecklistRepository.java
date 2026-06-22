package com.hms.repository.ot;

import com.hms.entity.ot.PreOpChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreOpChecklistRepository extends JpaRepository<PreOpChecklist, Long> {
    Optional<PreOpChecklist> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);
}
