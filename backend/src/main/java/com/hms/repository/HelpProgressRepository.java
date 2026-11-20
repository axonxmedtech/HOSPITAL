package com.hms.repository;

import com.hms.entity.HelpProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelpProgressRepository extends JpaRepository<HelpProgress, Long> {
    Optional<HelpProgress> findByScopeKey(String scopeKey);
}
