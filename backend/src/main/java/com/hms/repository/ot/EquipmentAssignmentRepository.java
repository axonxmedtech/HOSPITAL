package com.hms.repository.ot;

import com.hms.entity.ot.EquipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, Long> {
    List<EquipmentAssignment> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);

    @Query("""
        SELECT a FROM EquipmentAssignment a
        JOIN OtBooking b ON a.otBookingId = b.id
        WHERE a.hospitalId = :hospitalId
          AND a.equipmentId = :equipmentId
          AND b.status NOT IN ('CANCELLED', 'COMPLETED')
          AND (:bookingId IS NULL OR b.id <> :bookingId)
          AND b.scheduledStart < :end
          AND b.scheduledEnd > :start
    """)
    List<EquipmentAssignment> findEquipmentConflicts(Long hospitalId, Long equipmentId, LocalDateTime start, LocalDateTime end, Long bookingId);
}
