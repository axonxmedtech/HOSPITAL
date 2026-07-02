package com.hms.repository;

import com.hms.entity.QualityComplaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QualityComplaintRepository extends JpaRepository<QualityComplaint, Long> {
    Optional<QualityComplaint> findByIdAndHospitalId(Long id, Long hospitalId);

    List<QualityComplaint> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
