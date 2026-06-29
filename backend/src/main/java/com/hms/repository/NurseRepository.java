package com.hms.repository;

import com.hms.entity.Nurse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface NurseRepository extends JpaRepository<Nurse, Long> {
    Optional<Nurse> findByPublicId(String publicId);
    Page<Nurse> findByHospitalIdAndIsActiveTrue(Long hospitalId, Pageable pageable);
    List<Nurse> findByHospitalIdAndIsActiveTrue(Long hospitalId);
    Optional<Nurse> findByEmailAndIsActiveTrue(String email);

    @Query("SELECT MAX(CAST(SUBSTRING(n.customId, 4) AS int)) FROM Nurse n WHERE n.hospitalId = :hospitalId AND n.customId IS NOT NULL")
    Integer findMaxNurseSequence(@Param("hospitalId") Long hospitalId);
}
