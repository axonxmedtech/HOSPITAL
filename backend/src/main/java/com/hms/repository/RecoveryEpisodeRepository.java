package com.hms.repository;

import com.hms.entity.RecoveryEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryEpisodeRepository extends JpaRepository<RecoveryEpisode, Long> {
    Optional<RecoveryEpisode> findBySurgeryId(Long surgeryId);

    /**
     * Every patient currently in recovery at this hospital -- the "IN RECOVERY" section of the
     * board. An episode without dischargedAt is, by definition, active.
     */
    List<RecoveryEpisode> findByHospitalIdAndDischargedAtIsNullOrderByArrivedAtAsc(Long hospitalId);

    /** Whether a bay is currently occupied: does an active episode reference it. */
    Optional<RecoveryEpisode> findByRecoveryBayIdAndDischargedAtIsNull(Long recoveryBayId);

    @Query("SELECT COUNT(re) > 0 FROM RecoveryEpisode re "
            + "WHERE re.recoveryBayId = :bayId AND re.dischargedAt IS NULL")
    boolean existsActiveByRecoveryBayId(@Param("bayId") Long bayId);
}
