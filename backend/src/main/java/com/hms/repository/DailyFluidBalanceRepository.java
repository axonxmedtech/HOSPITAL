package com.hms.repository;

import com.hms.entity.DailyFluidBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * DailyFluidBalanceRepository - Repository interface for DailyFluidBalance.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface DailyFluidBalanceRepository extends JpaRepository<DailyFluidBalance, Long> {

    /**
     * Find daily balance summaries for admission.
     */
    List<DailyFluidBalance> findByHospitalIdAndAdmissionIdOrderByBalanceDateDesc(Long hospitalId, Long admissionId);

    /**
     * Find summary for specific date.
     */
    Optional<DailyFluidBalance> findByHospitalIdAndAdmissionIdAndBalanceDate(Long hospitalId, Long admissionId, LocalDate balanceDate);
}
