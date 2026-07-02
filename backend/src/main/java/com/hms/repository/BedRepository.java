package com.hms.repository;

import com.hms.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByWardIdAndHospitalId(Long wardId, Long hospitalId);
    List<Bed> findByHospitalIdAndStatus(Long hospitalId, String status);
    List<Bed> findByHospitalId(Long hospitalId);
    Optional<Bed> findByHospitalIdAndBedCodeIgnoreCase(Long hospitalId, String bedCode);
}
