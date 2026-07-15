package com.hms.repository;

import com.hms.entity.RecoveryEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecoveryEpisodeRepository extends JpaRepository<RecoveryEpisode, Long> {
    Optional<RecoveryEpisode> findBySurgeryId(Long surgeryId);
}
