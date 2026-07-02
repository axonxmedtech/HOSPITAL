package com.hms.repository;

import com.hms.entity.BreakdownTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BreakdownTicketRepository extends JpaRepository<BreakdownTicket, Long> {
    Optional<BreakdownTicket> findByIdAndHospitalId(Long id, Long hospitalId);

    List<BreakdownTicket> findByHospitalIdOrderByReportedAtDesc(Long hospitalId);
}
