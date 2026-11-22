package com.hms.repository;

import com.hms.entity.IpdPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IpdPackageRepository extends JpaRepository<IpdPackage, Long> {
    List<IpdPackage> findByHospitalIdOrderByNameAsc(Long hospitalId);
    List<IpdPackage> findByHospitalIdAndIsActiveTrueOrderByNameAsc(Long hospitalId);
    Optional<IpdPackage> findByIdAndHospitalId(Long id, Long hospitalId);
    boolean existsByHospitalIdAndNameIgnoreCaseAndIdNot(Long hospitalId, String name, Long id);
    boolean existsByHospitalIdAndNameIgnoreCase(Long hospitalId, String name);
}
