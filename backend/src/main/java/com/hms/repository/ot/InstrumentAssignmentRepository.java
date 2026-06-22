package com.hms.repository.ot;

import com.hms.entity.ot.InstrumentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface InstrumentAssignmentRepository extends JpaRepository<InstrumentAssignment, Long> {
    List<InstrumentAssignment> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);

    @Query("""
        SELECT a FROM InstrumentAssignment a
        JOIN OtBooking b ON a.otBookingId = b.id
        WHERE a.hospitalId = :hospitalId
          AND a.instrumentSetId = :instrumentSetId
          AND b.status NOT IN ('CANCELLED', 'COMPLETED')
          AND (:bookingId IS NULL OR b.id <> :bookingId)
          AND b.scheduledStart < :end
          AND b.scheduledEnd > :start
    """)
    List<InstrumentAssignment> findInstrumentConflicts(Long hospitalId, Long instrumentSetId, LocalDateTime start, LocalDateTime end, Long bookingId);
}
