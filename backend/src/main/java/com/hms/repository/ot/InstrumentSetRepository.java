package com.hms.repository.ot;

import com.hms.entity.ot.InstrumentSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentSetRepository extends JpaRepository<InstrumentSet, Long> {
    List<InstrumentSet> findByHospitalIdOrderByName(Long hospitalId);
    long countByHospitalIdAndStatusIgnoreCase(Long hospitalId, String status);
}
