package com.hms.repository;

import com.hms.entity.IpdBedHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IpdBedHistoryRepository extends JpaRepository<IpdBedHistory, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(h) > 0 FROM IpdBedHistory h, IpdAdmission a "
            + "WHERE h.ipdAdmissionId = a.id AND h.wardId = :wardId AND a.hospitalId = :hospitalId")
    boolean existsForWardAndHospital(@org.springframework.data.repository.query.Param("wardId") Long wardId,
                                    @org.springframework.data.repository.query.Param("hospitalId") Long hospitalId);

    List<IpdBedHistory> findByIpdAdmissionIdOrderByAssignedAtAsc(Long ipdAdmissionId);
    List<IpdBedHistory> findByIpdAdmissionIdInOrderByAssignedAtAsc(List<Long> ipdAdmissionIds);
    Optional<IpdBedHistory> findByIpdAdmissionIdAndReleasedAtIsNull(Long ipdAdmissionId);
}
