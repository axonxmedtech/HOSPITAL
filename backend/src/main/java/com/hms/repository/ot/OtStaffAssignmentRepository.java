package com.hms.repository.ot;

import com.hms.entity.ot.OtStaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OtStaffAssignmentRepository extends JpaRepository<OtStaffAssignment, Long> {
    List<OtStaffAssignment> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);
    long countByHospitalId(Long hospitalId);

    @Query("""
        SELECT a FROM OtStaffAssignment a
        JOIN OtBooking b ON a.otBookingId = b.id
        WHERE a.hospitalId = :hospitalId
          AND b.status NOT IN ('CANCELLED', 'COMPLETED')
          AND (:bookingId IS NULL OR b.id <> :bookingId)
          AND ((:staffUserId IS NOT NULL AND a.staffUserId = :staffUserId)
               OR (:doctorId IS NOT NULL AND a.doctorId = :doctorId)
               OR LOWER(a.staffName) = LOWER(:staffName))
          AND b.scheduledStart < :end
          AND b.scheduledEnd > :start
    """)
    List<OtStaffAssignment> findStaffConflicts(Long hospitalId, Long staffUserId, Long doctorId, String staffName, LocalDateTime start, LocalDateTime end, Long bookingId);
}
