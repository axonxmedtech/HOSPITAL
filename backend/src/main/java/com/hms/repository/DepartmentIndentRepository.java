package com.hms.repository;

import com.hms.entity.DepartmentIndent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentIndentRepository extends JpaRepository<DepartmentIndent, Long> {
    List<DepartmentIndent> findByHospitalId(Long hospitalId);
    Optional<DepartmentIndent> findByIdAndHospitalId(Long id, Long hospitalId);
    List<DepartmentIndent> findByHospitalIdAndStatus(Long hospitalId, String status);
}
