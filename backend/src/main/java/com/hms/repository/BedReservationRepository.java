package com.hms.repository;

import com.hms.entity.BedReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BedReservationRepository extends JpaRepository<BedReservation, Long> {
    /** Holds in force now for these beds. */
    List<BedReservation> findByHospitalIdAndBedIdInAndStatusAndReservedUntilAfter(
            Long hospitalId, Collection<Long> bedIds, String status, LocalDateTime now);
    List<BedReservation> findByHospitalIdAndStatusAndReservedUntilAfterOrderByExpectedAtAsc(
            Long hospitalId, String status, LocalDateTime now);
    Optional<BedReservation> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
}
