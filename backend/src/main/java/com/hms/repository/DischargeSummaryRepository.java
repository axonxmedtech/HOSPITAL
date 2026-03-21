package com.hms.repository;

import com.hms.entity.DischargeSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, Long> {
}
