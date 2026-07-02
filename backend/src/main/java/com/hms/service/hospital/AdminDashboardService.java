package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.exception.UnauthorizedException;
import com.hms.entity.ExecutiveAlert;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Hospital Administration & Executive/Clinical MIS Dashboard (Form 32 core). Reads and
 * aggregates existing transactional tables — never owns clinical/billing records (per the
 * blueprint's "operational database aggregation" principle) — plus a manual/triggered
 * {@link ExecutiveAlert} lifecycle (BR-5 escalation, BR-6 immutable once resolved).
 *
 * Scope note: `kpi_snapshot` nightly-cron historical trending and `dashboard_widget`
 * drag-drop layout customization (BR-2/BR-4) are deferred — both need dedicated
 * infrastructure (a new scheduled job, a persisted-snapshot ledger) beyond a single-pass
 * increment. Lab/Radiology TAT and true OT utilization (actual start/end vs scheduled) are
 * also deferred: today's `updatedAt` timestamps are generic mutation timestamps, not
 * stage-specific completion times, so a TAT computed from them would be misleading to an
 * executive audience rather than merely incomplete — safer to omit than to show a wrong
 * number. OT load is reported here as booking-status counts instead of a utilization rate.
 */
@Service
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);

    private static final Set<String> VALID_SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");
    private static final Set<String> VALID_ALERT_STATUSES = Set.of("ACKNOWLEDGED", "RESOLVED");

    @Autowired private BedRepository bedRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private OtBookingRepository otBookingRepository;
    @Autowired private com.hms.repository.pharmacy.MedicineBatchRepository medicineBatchRepository;
    @Autowired private ExecutiveAlertRepository alertRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;

    public ExecutiveDashboardResponse getExecutiveDashboard(String timeframe) {
        Long hospitalId = requireHospital();
        String tf = normalizeTimeframe(timeframe);
        LocalDateTime since = timeframeStart(tf);

        List<com.hms.entity.Bed> beds = bedRepository.findByHospitalId(hospitalId);
        long total = beds.size();
        long occupied = beds.stream().filter(b -> "occupied".equalsIgnoreCase(b.getStatus())).count();

        BigDecimal revenue = billingRepository.sumAmountByHospitalIdAndPaymentStatusSince(hospitalId, "PAID", since);
        BigDecimal outstanding = billingRepository.sumAmountByHospitalIdAndPaymentStatusSince(hospitalId, "PENDING", since);
        long expiring = medicineBatchRepository.findExpiringSoon(hospitalId, LocalDate.now().plusDays(30), PageRequest.of(0, 1)).getTotalElements();

        return new ExecutiveDashboardResponse(
                tf,
                total,
                occupied,
                occupancyRate(occupied, total),
                revenue == null ? BigDecimal.ZERO : revenue,
                outstanding == null ? BigDecimal.ZERO : outstanding,
                expiring,
                alertRepository.countByHospitalIdAndStatus(hospitalId, "ACTIVE"),
                alertRepository.countByHospitalIdAndStatusAndSeverity(hospitalId, "ACTIVE", "CRITICAL"));
    }

    public ClinicalDashboardResponse getClinicalDashboard() {
        Long hospitalId = requireHospital();
        List<com.hms.entity.Bed> beds = bedRepository.findByHospitalId(hospitalId);
        long total = beds.size();
        long occupied = beds.stream().filter(b -> "occupied".equalsIgnoreCase(b.getStatus())).count();

        return new ClinicalDashboardResponse(
                total,
                occupied,
                occupancyRate(occupied, total),
                otBookingRepository.countByHospitalIdAndStatus(hospitalId, "SCHEDULED"),
                otBookingRepository.countByHospitalIdAndStatus(hospitalId, "COMPLETED"),
                otBookingRepository.countByHospitalIdAndStatus(hospitalId, "CANCELLED"),
                alertRepository.countByHospitalIdAndStatus(hospitalId, "ACTIVE"));
    }

    @Transactional
    public ExecutiveAlert createAlert(ExecutiveAlertRequest request) {
        Long hospitalId = requireHospital();
        String severity = request.getSeverity() == null ? "" : request.getSeverity().toUpperCase();
        if (!VALID_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("Severity must be one of " + VALID_SEVERITIES);
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }

        ExecutiveAlert alert = new ExecutiveAlert();
        alert.setHospitalId(hospitalId);
        alert.setSeverity(severity);
        alert.setTitle(request.getTitle());
        alert.setDescription(request.getDescription());
        alert.setStatus("ACTIVE");
        ExecutiveAlert saved = alertRepository.save(alert);
        audit("ADMIN_DASHBOARD_ALERT_CREATED", severity + " alert: " + saved.getTitle(), hospitalId);
        return saved;
    }

    public List<ExecutiveAlert> getAlerts() {
        return alertRepository.findByHospitalIdOrderByCreatedAtDesc(requireHospital());
    }

    /** BR-6: resolved alerts are immutable — acknowledgment requires a remark explaining the action. */
    @Transactional
    public ExecutiveAlert acknowledgeAlert(AlertAcknowledgeRequest request) {
        Long hospitalId = requireHospital();
        if (request.getAlertId() == null) {
            throw new IllegalArgumentException("Alert ID is required");
        }
        ExecutiveAlert alert = alertRepository.findByIdAndHospitalId(request.getAlertId(), hospitalId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new IllegalStateException("This alert has already been resolved and is immutable (BR-6)");
        }
        String status = request.getStatus() == null ? "" : request.getStatus().toUpperCase();
        if (!VALID_ALERT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status must be ACKNOWLEDGED or RESOLVED");
        }
        if (request.getRemarks() == null || request.getRemarks().isBlank()) {
            throw new IllegalArgumentException("A remark explaining the action is required");
        }

        alert.setStatus(status);
        alert.setRemarks(request.getRemarks());
        if ("RESOLVED".equals(status)) {
            alert.setResolvedAt(LocalDateTime.now());
        }
        ExecutiveAlert saved = alertRepository.save(alert);
        audit("ADMIN_DASHBOARD_ALERT_" + status, "Alert #" + alert.getId() + ": " + request.getRemarks(), hospitalId);
        return saved;
    }

    // ===== Helpers =====

    private BigDecimal occupancyRate(long occupied, long total) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(occupied).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String normalizeTimeframe(String timeframe) {
        String tf = timeframe == null ? "TODAY" : timeframe.toUpperCase();
        if (!Set.of("TODAY", "WEEKLY", "MONTHLY", "YEARLY").contains(tf)) {
            throw new IllegalArgumentException("Timeframe must be TODAY, WEEKLY, MONTHLY, or YEARLY");
        }
        return tf;
    }

    private LocalDateTime timeframeStart(String timeframe) {
        LocalDate today = LocalDate.now();
        return switch (timeframe) {
            case "WEEKLY" -> today.minusDays(7).atStartOfDay();
            case "MONTHLY" -> today.minusMonths(1).atStartOfDay();
            case "YEARLY" -> today.minusYears(1).atStartOfDay();
            default -> LocalDateTime.of(today, LocalTime.MIN);
        };
    }

    private Long requireHospital() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return hospitalId;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            com.hms.entity.AuditLog entry = new com.hms.entity.AuditLog();
            entry.setAction(action);
            entry.setDetails(details);
            entry.setPerformedBy(securityHelper.getCurrentUserEmail());
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
