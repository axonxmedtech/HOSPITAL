package com.hms.repository;

import com.hms.entity.IcuWard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for dedicated ICU wards. Every finder is tenant-scoped by hospital_id.
 */
@Repository
public interface IcuWardRepository extends JpaRepository<IcuWard, Long> {

    List<IcuWard> findByHospitalId(Long hospitalId);

    Optional<IcuWard> findByIdAndHospitalId(Long id, Long hospitalId);

    Optional<IcuWard> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    Optional<IcuWard> findByWardIdAndHospitalId(Long wardId, Long hospitalId);

    boolean existsByHospitalIdAndWardNameIgnoreCase(Long hospitalId, String wardName);

    boolean existsByHospitalIdAndWardNameIgnoreCaseAndIdNot(Long hospitalId, String wardName, Long id);
}
