package com.hms.repository;

import com.hms.entity.NurseWardAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NurseWardAssignmentRepository extends JpaRepository<NurseWardAssignment, Long> {
    List<NurseWardAssignment> findByNurseId(Long nurseId);
    Optional<NurseWardAssignment> findByNurseIdAndWardId(Long nurseId, Long wardId);
    void deleteByNurseId(Long nurseId);
}
