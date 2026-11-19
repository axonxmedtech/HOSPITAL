package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    /** Never back-fill more than this many days for one admission in one run. */
    private static final int MAX_CATCH_UP_DAYS = 366;

    private static final java.util.Set<String> ACTIVE = java.util.Set.of("ADMITTED", "DISCHARGE_PLANNED");

    /**
     * Runs at midnight IST (12:00 AM IST) to calculate charges for the day just ended.
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    public void processDailyBedCharges() {
        logger.info("Executing daily bed charge processing scheduled task...");

        // Only patients still in the hospital accrue bed days.
        List<IpdAdmission> activeAdmissions = ipdAdmissionRepository.findByStatusIn(ACTIVE);
        LocalDate today = LocalDate.now();

        int processed = 0;

        for (IpdAdmission admission : activeAdmissions) {
            // One transaction per admission. The whole run was one transaction, so a database
            // error on any single admission marked it rollback-only and every other hospital's
            // charges for the night were lost at commit, despite this catch.
            try {
                transactionTemplate.executeWithoutResult(
                        status -> processAdmissionCharge(admission, today));
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
     * Adds any bed days this admission is missing up to today. Called at discharge (before the
     * outstanding balance is checked) and when the IPD bill is opened.
     */
    public void catchUpAdmission(IpdAdmission admission) {
        if (admission == null || !ACTIVE.contains(String.valueOf(admission.getStatus()).toUpperCase())) return;
        // Its own transaction: the charges are owed whatever happens next. Joining the discharge
        // transaction meant a refused discharge ("settle ₹9000 first") rolled them back, and the
        // bill on screen went on showing the old, smaller total.
        org.springframework.transaction.support.TransactionTemplate own =
                new org.springframework.transaction.support.TransactionTemplate(transactionTemplate.getTransactionManager());
        own.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        own.executeWithoutResult(status -> processAdmissionCharge(admission, LocalDate.now()));
    }

    /**
     * Charges every calendar day from the day after admission up to {@code today} that has no
     * "Daily Bed Charge" line yet (the admission day itself is the "Bed Price" line). Each day is
     * priced at the ward the patient was in at the start of that day, from the bed history, so
     * an ICU spell is billed at the ICU rate even after a step-down.
     */
    private void processAdmissionCharge(IpdAdmission admission, LocalDate today) {
        // 1. Fetch existing bill for this IPD
        List<Billing> bills = billingRepository.findByIpdAdmissionId(admission.getId());
        Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;

        if (bill == null) {
            logger.warn("No primary bill found for IPD admission {}, skipping bed charge.", admission.getId());
            return;
        }

        // A written-off or cancelled bill takes no further charges.
        if ("CLOSED".equalsIgnoreCase(bill.getPaymentStatus()) || "CANCELLED".equalsIgnoreCase(bill.getPaymentStatus())) {
            return;
        }

        // 2. Idempotency: a day already charged is never charged again.
        java.util.Set<String> charged = new java.util.HashSet<>();
        for (BillingItem item : billingItemRepository.findByBillingId(bill.getId())) {
            if (item.getDescription() != null) charged.add(item.getDescription());
        }

        LocalDate first = admission.getAdmissionDatetime() != null
                ? admission.getAdmissionDatetime().toLocalDate().plusDays(1)
                : today;
        if (first.isBefore(today.minusDays(MAX_CATCH_UP_DAYS))) first = today.minusDays(MAX_CATCH_UP_DAYS);

        List<IpdBedHistory> history = admission.getId() == null ? List.of()
                : ipdBedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(admission.getId());

        boolean added = false;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            String description = "Daily Bed Charge - " + day.format(DateTimeFormatter.ISO_DATE);
            if (charged.contains(description)) continue;

            // 3. Resolve price from the ward occupied at the start of that day.
            BigDecimal bedPrice = BigDecimal.ZERO;
            Long wardId = wardAt(history, day.atStartOfDay(), admission.getWardId());
            if (wardId != null) {
                Ward ward = wardRepository.findById(wardId).orElse(null);
                if (ward != null && ward.getBedPrice() != null) {
                    bedPrice = ward.getBedPrice();
                }
            }
            if (bedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                logger.info("Bed price is zero for ward linked to admission {}, skipping {}.", admission.getId(), day);
                continue;
            }

            // 4. Record the new Billing Item.
            BillingItem newItem = new BillingItem();
            newItem.setBillingId(bill.getId());
            newItem.setHospitalId(bill.getHospitalId());
            newItem.setDescription(description);
            newItem.setAmount(bedPrice);
            billingItemRepository.save(newItem);
            charged.add(description);
            added = true;
        }

        // 5. Re-derive the total AND the status from the lines and the ledger. Bumping only the
        // amount left a settled bill reading PAID while today's charge was owed, and the
        // dashboards offer "Mark Paid" only for PENDING/PARTIAL bills, so it was never collected.
        if (added) {
            billingService.recalculateTotal(bill.getId());
        }
    }

    /** The ward the patient was in at {@code at}; the current ward when history has no answer. */
    private static Long wardAt(List<IpdBedHistory> history, java.time.LocalDateTime at, Long fallback) {
        Long ward = null;
        for (IpdBedHistory h : history) {
            if (h.getAssignedAt() == null || h.getAssignedAt().isAfter(at)) continue;
            if (h.getReleasedAt() != null && !h.getReleasedAt().isAfter(at)) continue;
            ward = h.getWardId();
        }
        return ward != null ? ward : fallback;
    }
}
