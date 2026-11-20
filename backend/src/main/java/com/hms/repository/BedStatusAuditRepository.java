package com.hms.repository;
import com.hms.entity.BedStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BedStatusAuditRepository extends JpaRepository<BedStatusAudit, Long> {
    List<BedStatusAudit> findByBedIdOrderByChangedAtDesc(Long bedId);

    /** Latest status change per bed, for the given beds of one hospital: rows of [bedId, changedAt]. */
    @org.springframework.data.jpa.repository.Query("SELECT a.bedId, MAX(a.changedAt) FROM BedStatusAudit a "
            + "WHERE a.hospitalId = :hospitalId AND a.bedId IN :bedIds GROUP BY a.bedId")
    List<Object[]> latestChangePerBed(@org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("bedIds") java.util.Collection<Long> bedIds);
}
