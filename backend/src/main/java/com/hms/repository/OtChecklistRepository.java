package com.hms.repository;

import com.hms.entity.OtChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OtChecklistRepository extends JpaRepository<OtChecklist, Long> {
    Optional<OtChecklist> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
