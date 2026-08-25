package com.hms.repository;

import com.hms.entity.IcuStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ICU Phase 3. Every finder is tenant-scoped; nothing here resolves a bare id. */
public interface IcuStayRepository extends JpaRepository<IcuStay, Long> {

    Optional<IcuStay> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    Optional<IcuStay> findByIpdAdmissionIdAndHospitalIdAndStatus(
            Long ipdAdmissionId, Long hospitalId, String status);

    List<IcuStay> findByIpdAdmissionIdAndHospitalIdOrderByAdmittedAtDesc(
            Long ipdAdmissionId, Long hospitalId);

    /** Active stays for a set of admissions — the ICU board's batched read. */
    List<IcuStay> findByHospitalIdAndStatusAndIpdAdmissionIdIn(
            Long hospitalId, String status, Collection<Long> ipdAdmissionIds);

    boolean existsByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);
}
