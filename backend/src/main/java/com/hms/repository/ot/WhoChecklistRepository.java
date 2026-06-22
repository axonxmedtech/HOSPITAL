package com.hms.repository.ot;

import com.hms.entity.ot.WhoChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhoChecklistRepository extends JpaRepository<WhoChecklist, Long> {
    Optional<WhoChecklist> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);
}
