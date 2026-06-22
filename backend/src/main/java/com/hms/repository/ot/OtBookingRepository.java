package com.hms.repository.ot;

import com.hms.entity.ot.OtBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OtBookingRepository extends JpaRepository<OtBooking, Long> {
    List<OtBooking> findByHospitalIdOrderByScheduledStartDesc(Long hospitalId);
    List<OtBooking> findByHospitalIdAndScheduledStartBetweenOrderByScheduledStart(Long hospitalId, LocalDateTime start, LocalDateTime end);
    long countByHospitalIdAndScheduledStartBetween(Long hospitalId, LocalDateTime start, LocalDateTime end);
    long countByHospitalIdAndStatusIgnoreCase(Long hospitalId, String status);
    long countByHospitalIdAndPriorityIgnoreCase(Long hospitalId, String priority);
    long countByHospitalIdAndClearanceStatusIgnoreCase(Long hospitalId, String clearanceStatus);

    @Query("""
        SELECT b FROM OtBooking b
        WHERE b.hospitalId = :hospitalId
          AND b.otRoomId = :roomId
          AND b.status NOT IN ('CANCELLED', 'COMPLETED')
          AND (:bookingId IS NULL OR b.id <> :bookingId)
          AND b.scheduledStart < :end
          AND b.scheduledEnd > :start
    """)
    List<OtBooking> findRoomConflicts(Long hospitalId, Long roomId, LocalDateTime start, LocalDateTime end, Long bookingId);
}
