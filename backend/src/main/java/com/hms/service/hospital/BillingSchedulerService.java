package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BillingSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(BillingSchedulerService.class);

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private BillingItemRepository billingItemRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private IpdBedHistoryRepository ipdBedHistoryRepository;

    private static final java.util.Set<String> ACTIVE = java.util.Set.of("ADMITTED", "DISCHARGE_PLANNED");

    @Autowired(required = false)
    private com.hms.repository.IpdPackageRepository packageRepository;

    /**
     * Runs at midnight IST (12:00 AM IST) to post the charges of the day that has just begun.
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    public void processDailyBedCharges() {
        logger.info("Executing daily bed charge processing scheduled task...");

        // Only patients still in the hospital accrue bed days.
        List<IpdAdmission> activeAdmissions = ipdAdmissionRepository.findByStatusIn(ACTIVE);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault());

        int processed = 0;

        for (IpdAdmission admission : activeAdmissions) {
            // One transaction per admission. The whole run was one transaction, so a database
            // error on any single admission marked it rollback-only and every other hospital's
            // charges for the night were lost at commit, despite this catch.
            try {
                transactionTemplate.executeWithoutResult(
                        status -> processAdmissionCharge(admission, now));
                processed++;
            } catch (Exception e) {
                logger.error("Failed to process bed charge for admission ID: {}", admission.getId(), e);
            }
        }

        logger.info("Daily bed charge task completed. Processed {} active admissions.", processed);
    }

    /**
     * The midnight run used to be the only thing that ever added a bed day, so any night the
     * server was down (a restart, a laptop at a demo) was a day that was never billed. Catching up
     * on start-up and at discharge makes the charges depend on the calendar, not on uptime.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        try {
            processDailyBedCharges();
        } catch (Exception e) {
            logger.error("Bed charge catch-up on start-up failed", e);
        }
    }

    /**
     * Adds any bed and nursing days this admission is missing up to now. Called at discharge
     * (before the outstanding balance is checked) and when the IPD bill is opened.
     */
    public void catchUpAdmission(IpdAdmission admission) {
        if (admission == null || !ACTIVE.contains(String.valueOf(admission.getStatus()).toUpperCase())) return;
        // Its own transaction: the charges are owed whatever happens next. Joining the discharge
        // transaction meant a refused discharge ("settle Rs 9000 first") rolled them back, and the
        // bill on screen went on showing the old, smaller total.
        org.springframework.transaction.support.TransactionTemplate own =
                new org.springframework.transaction.support.TransactionTemplate(transactionTemplate.getTransactionManager());
        own.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        own.executeWithoutResult(status -> processAdmissionCharge(admission,
                java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())));
    }

    /**
     * Charges every day that has begun (see {@link IpdChargePeriods}) and has no line yet: the
     * bed from day 2 (day 1 is the "Bed Price" line written at admission) and the ward's nursing
     * charge from day 1. Each day is priced at the ward the patient was in when that day began,
     * from the bed history, so an ICU spell is billed at the ICU rate even after a step-down.
     * Days inside a package are covered by it and not charged.
     */
    private void processAdmissionCharge(IpdAdmission admission, java.time.LocalDateTime now) {
        List<Billing> bills = billingRepository.findByIpdAdmissionId(admission.getId());
        Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        if (!takesCharges(bill, admission)) return;

        // Idempotency: a day already charged is never charged again.
        java.util.Set<String> charged = new java.util.HashSet<>();
        for (BillingItem item : billingItemRepository.findByBillingId(bill.getId())) {
            if (item.getDescription() != null) charged.add(item.getDescription());
        }
        List<IpdBedHistory> history = admission.getId() == null ? List.of()
                : ipdBedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(admission.getId());
        int covered = coveredDays(admission);

        boolean added = false;
        for (IpdChargePeriods.Period day : IpdChargePeriods.started(admission, now)) {
            if (day.number() <= covered) continue;
            Ward ward = wardAt(history, day.start(), admission.getWardId());
            if (day.number() > 1 || admission.getAdmissionDatetime() == null) {
                String bedLine = admission.getAdmissionDatetime() == null
                        ? "Daily Bed Charge - " + day.label() : IpdChargePeriods.bedLine(admission, day);
                added |= addOnce(bill, charged, bedLine, ward == null ? null : ward.getBedPrice());
            }
            if (admission.getAdmissionDatetime() != null) {
                added |= addOnce(bill, charged, IpdChargePeriods.nursingLine(admission, day),
                        ward == null ? null : ward.getNursingCharge());
            }
        }

        // Re-derive the total AND the status from the lines and the ledger. Bumping only the
        // amount left a settled bill reading PAID while today's charge was owed, and the
        // dashboards offer "Mark Paid" only for PENDING/PARTIAL bills, so it was never collected.
        if (added) {
            billingService.recalculateTotal(bill.getId());
        }
    }

    /** Days the admission's package covers; 0 without one. */
    private int coveredDays(IpdAdmission admission) {
        if (admission.getPackageId() == null || packageRepository == null) return 0;
        return packageRepository.findByIdAndHospitalId(admission.getPackageId(), admission.getHospitalId())
                .map(com.hms.entity.IpdPackage::getIncludedDays).orElse(0);
    }

    /** Adds the line unless already charged; a free ward (no price) adds nothing. */
    private boolean addOnce(Billing bill, java.util.Set<String> charged, String description, BigDecimal price) {
        if (charged.contains(description) || price == null || price.signum() <= 0) return false;
        BillingItem item = new BillingItem();
        item.setBillingId(bill.getId());
        item.setHospitalId(bill.getHospitalId());
        item.setDescription(description);
        item.setAmount(price);
        billingItemRepository.save(item);
        charged.add(description);
        return true;
    }

    /** A missing, written-off or cancelled bill takes no further charges. */
    private static boolean takesCharges(Billing bill, IpdAdmission admission) {
        if (bill == null) {
            logger.warn("No primary bill found for IPD admission {}, skipping bed charge.", admission.getId());
            return false;
        }
        String status = bill.getPaymentStatus();
        return !"CLOSED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status);
    }

    /** The ward the patient was in at {@code at} (the current ward when history has no answer). */
    private Ward wardAt(List<IpdBedHistory> history, java.time.LocalDateTime at, Long fallback) {
        Long wardId = wardIdAt(history, at, fallback);
        return wardId == null ? null : wardRepository.findById(wardId).orElse(null);
    }

    /** The ward the patient was in at {@code at}; the current ward when history has no answer. */
    private static Long wardIdAt(List<IpdBedHistory> history, java.time.LocalDateTime at, Long fallback) {
        Long ward = null;
        for (IpdBedHistory h : history) {
            boolean started = h.getAssignedAt() != null && !h.getAssignedAt().isAfter(at);
            boolean stillThere = h.getReleasedAt() == null || h.getReleasedAt().isAfter(at);
            if (started && stillThere) ward = h.getWardId();
        }
        return ward != null ? ward : fallback;
    }
}
