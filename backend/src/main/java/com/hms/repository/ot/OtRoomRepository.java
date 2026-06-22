package com.hms.repository.ot;

import com.hms.entity.ot.OtRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtRoomRepository extends JpaRepository<OtRoom, Long> {
    List<OtRoom> findByHospitalIdOrderByName(Long hospitalId);
    long countByHospitalIdAndStatusIgnoreCase(Long hospitalId, String status);
}
