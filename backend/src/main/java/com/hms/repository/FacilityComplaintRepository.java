package com.hms.repository;

import com.hms.entity.FacilityComplaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityComplaintRepository extends JpaRepository<FacilityComplaint, Long> {
    Optional<FacilityComplaint> findByIdAndHospitalId(Long id, Long hospitalId);

    List<FacilityComplaint> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
