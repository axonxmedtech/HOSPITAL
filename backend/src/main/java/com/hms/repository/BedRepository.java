package com.hms.repository;

import com.hms.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BedRepository extends JpaRepository<Bed, Long> {
    @org.springframework.data.jpa.repository.Query(
            "SELECT b.bedId FROM Bed b WHERE b.wardId = :wardId AND b.hospitalId = :hospitalId "
          + "AND LOWER(b.status) = 'available' ORDER BY b.bedId")
    List<Long> findAvailableBedIdsInWard(
            @org.springframework.data.repository.query.Param("wardId") Long wardId,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT b FROM Bed b WHERE b.bedId = :bedId AND b.hospitalId = :hospitalId")
    java.util.Optional<Bed> findByBedIdAndHospitalIdForUpdate(
            @org.springframework.data.repository.query.Param("bedId") Long bedId,
            @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId);

    List<Bed> findByWardIdAndHospitalId(Long wardId, Long hospitalId);
    List<Bed> findByHospitalIdAndStatus(Long hospitalId, String status);
    List<Bed> findByHospitalId(Long hospitalId);

    /**
     * Tenant-scoped lookup by id. A bed id arrives from the client on admission and bed
     * transfer, so resolving it with a bare findById let one hospital name another's bed:
     * the admission row was written before the tenant-scoped BedStatusService refused, and
     * with a foreign ward it was not refused at all.
     */
    java.util.Optional<Bed> findByBedIdAndHospitalId(Long bedId, Long hospitalId);
}
