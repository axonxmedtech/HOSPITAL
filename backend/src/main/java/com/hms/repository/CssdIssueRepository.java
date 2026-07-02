package com.hms.repository;

import com.hms.entity.CssdIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CssdIssueRepository extends JpaRepository<CssdIssue, Long> {
    List<CssdIssue> findByHospitalIdOrderByIssueTimeDesc(Long hospitalId);
}
