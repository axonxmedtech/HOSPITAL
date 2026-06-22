package com.hms.repository.ot;

import com.hms.entity.ot.RecoveryRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryRoomRepository extends JpaRepository<RecoveryRoom, Long> {
    List<RecoveryRoom> findByHospitalIdAndOtBookingIdOrderByCreatedAtDesc(Long hospitalId, Long otBookingId);
}
