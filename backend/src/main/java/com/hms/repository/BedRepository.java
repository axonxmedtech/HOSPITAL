package com.hms.repository;

import com.hms.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BedRepository extends JpaRepository<Bed, Long> {
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

    /**
     * ICU Phase 2 — every bed of a set of wards, in one tenant-scoped query. The existing code
     * fetches ward by ward (or fetches the hospital's whole bed list and filters in Java); the
     * ICU board reads N units per request, so it asks once.
     */
    List<Bed> findByHospitalIdAndWardIdIn(Long hospitalId, java.util.Collection<Long> wardIds);
}
