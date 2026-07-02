package com.hms.repository;

import com.hms.entity.ProgressOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgressOrderRepository extends JpaRepository<ProgressOrder, Long> {
    List<ProgressOrder> findByHospitalIdAndProgressNoteId(Long hospitalId, Long progressNoteId);
}
