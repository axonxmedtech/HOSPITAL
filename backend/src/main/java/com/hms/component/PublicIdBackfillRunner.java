package com.hms.component;

import com.hms.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * PublicIdBackfillRunner - Backfills missing public and custom IDs on startup in bounded batches.
 *
 * <p><b>Hardened Production Implementation (Task P0-03):</b>
 * <ul>
 *   <li><b>Bounded Memory (No OOM):</b> Replaced unconstrained {@code findAll()} with cursor-based
 *       batched queries ({@value #BATCH_SIZE} rows/chunk). Memory usage remains constant even with
 *       millions of audit logs or patient records, safely respecting the 512 MB production heap ceiling.</li>
 *   <li><b>Targeted Filtering:</b> Queries ONLY records where {@code publicId} or {@code customId}
 *       is missing. Once backfilled, startup executes one fast query per table returning 0 rows in milliseconds.</li>
 *   <li><b>Strict Idempotency:</b> Existing valid public and custom IDs are never overwritten.</li>
 *   <li><b>Zero Business Data Mutation:</b> Removed historical unwanted mutations that overwrote
 *       free consultation fees (0.00 -> 500.00) or defaulted tenant modules. Backfills strictly identifiers.</li>
 *   <li><b>Safe Transactions:</b> Each batch commits in its own transaction boundary with
 *       {@code entityManager.clear()} to prevent persistence context memory retention.</li>
 * </ul>
 */
@Component
public class PublicIdBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);

    public static final int BATCH_SIZE = 250;
    private static final SecureRandom FALLBACK_RANDOM = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${hms.backfill.public-id.enabled:true}")
    private boolean backfillEnabled = true;

    @Override
    public void run(String... args) throws Exception {
        if (!backfillEnabled) {
            log.info("PublicIdBackfillRunner: backfill is disabled via configuration.");
            return;
        }

        log.info("PublicIdBackfillRunner: starting bounded ID backfill check...");
        long totalStart = System.currentTimeMillis();

        int hospitals = backfillHospitals();
        int users = backfillUsers();
        int doctors = backfillDoctors();
        int patients = backfillPatients();
        int appointments = backfillAppointments();
        int billings = backfillBillings();
        int auditLogs = backfillAuditLogs();

        long elapsed = System.currentTimeMillis() - totalStart;
        int totalUpdated = hospitals + users + doctors + patients + appointments + billings + auditLogs;

        if (totalUpdated > 0) {
            log.info("PublicIdBackfillRunner completed: updated {} total record(s) across tables in {} ms",
                    totalUpdated, elapsed);
        } else {
            log.info("PublicIdBackfillRunner completed: all records have valid public IDs (0 updates needed) in {} ms",
                    elapsed);
        }
    }

    private int backfillHospitals() {
        return backfillEntity(
                "Hospital",
                "e.publicId IS NULL OR e.publicId = '' OR e.customId IS NULL",
                Hospital.class,
                Hospital::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty()) {
                        e.setPublicId(UUID.randomUUID().toString());
                    }
                    if (e.getCustomId() == null) {
                        e.generateIds();
                    }
                }
        );
    }

    private int backfillUsers() {
        return backfillEntity(
                "User",
                "e.publicId IS NULL OR e.publicId = '' OR e.publicId = 'null' OR e.customId IS NULL",
                User.class,
                User::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty() || "null".equalsIgnoreCase(e.getPublicId())) {
                        e.setPublicId(UUID.randomUUID().toString());
                    }
                    if (e.getCustomId() == null) {
                        e.generateIds();
                    }
                }
        );
    }

    private int backfillDoctors() {
        return backfillEntity(
                "Doctor",
                "e.publicId IS NULL OR e.publicId = '' OR e.customId IS NULL",
                Doctor.class,
                Doctor::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty()) {
                        e.setPublicId(UUID.randomUUID().toString());
                    }
                    if (e.getCustomId() == null) {
                        e.setCustomId("DOC" + (e.getId() != null ? e.getId() : (1000 + FALLBACK_RANDOM.nextInt(9000))));
                    }
                }
        );
    }

    private int backfillPatients() {
        return backfillEntity(
                "Patient",
                "e.publicId IS NULL OR e.publicId = '' OR e.customId IS NULL",
                Patient.class,
                Patient::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty()) {
                        e.setPublicId(UUID.randomUUID().toString());
                    }
                    if (e.getCustomId() == null) {
                        e.setCustomId("PAT" + (e.getId() != null ? e.getId() : (1000 + FALLBACK_RANDOM.nextInt(9000))));
                    }
                }
        );
    }

    private int backfillAppointments() {
        return backfillEntity(
                "Appointment",
                "e.publicId IS NULL OR e.publicId = '' OR e.customId IS NULL",
                Appointment.class,
                Appointment::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty() || e.getCustomId() == null) {
                        e.generateIds();
                    }
                }
        );
    }

    private int backfillBillings() {
        return backfillEntity(
                "Billing",
                "e.publicId IS NULL OR e.publicId = '' OR e.customId IS NULL",
                Billing.class,
                Billing::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty() || e.getCustomId() == null) {
                        e.generateIds();
                    }
                }
        );
    }

    private int backfillAuditLogs() {
        return backfillEntity(
                "AuditLog",
                "e.publicId IS NULL OR e.publicId = ''",
                AuditLog.class,
                AuditLog::getId,
                e -> {
                    if (e.getPublicId() == null || e.getPublicId().trim().isEmpty()) {
                        e.generatePublicId();
                    }
                }
        );
    }

    /**
     * Executes batched ID-windowed queries to find and backfill records with missing public/custom IDs.
     *
     * <p>Each batch of {@value #BATCH_SIZE} records is loaded, updated, merged, flushed, and evicted
     * from the persistence context within an independent transaction boundary.
     *
     * @param entityName the JPA entity name
     * @param missingCondition JPQL predicate for missing IDs
     * @param entityClass the entity class
     * @param idExtractor extracts the numeric primary key for cursor pagination
     * @param backfiller mutator function assigning missing IDs
     * @return the number of updated records
     */
    private <T> int backfillEntity(
            String entityName,
            String missingCondition,
            Class<T> entityClass,
            Function<T, Long> idExtractor,
            Consumer<T> backfiller) {

        long lastId = 0L;
        int totalUpdated = 0;
        int totalExamined = 0;
        long startTime = System.currentTimeMillis();

        try {
            while (true) {
                final long currentLastId = lastId;
                List<T> batch = entityManager.createQuery(
                        "SELECT e FROM " + entityName + " e WHERE (" + missingCondition + ") AND e.id > :lastId ORDER BY e.id ASC",
                        entityClass)
                        .setParameter("lastId", currentLastId)
                        .setMaxResults(BATCH_SIZE)
                        .getResultList();

                if (batch.isEmpty()) {
                    break;
                }

                totalExamined += batch.size();
                Long batchMaxId = idExtractor.apply(batch.get(batch.size() - 1));
                if (batchMaxId == null || batchMaxId <= currentLastId) {
                    log.warn("PublicIdBackfillRunner [{}]: non-increasing ID {} detected, aborting loop to prevent hang",
                            entityName, batchMaxId);
                    break;
                }
                lastId = batchMaxId;

                Integer updatedInBatch = transactionTemplate.execute(status -> {
                    int count = 0;
                    for (T entity : batch) {
                        backfiller.accept(entity);
                        entityManager.merge(entity);
                        count++;
                    }
                    entityManager.flush();
                    entityManager.clear();
                    return count;
                });

                if (updatedInBatch != null) {
                    totalUpdated += updatedInBatch;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            if (totalUpdated > 0) {
                log.info("PublicIdBackfillRunner [{}]: examined {}, updated {} record(s) in {} ms",
                        entityName, totalExamined, totalUpdated, duration);
            } else {
                log.debug("PublicIdBackfillRunner [{}]: all records contain valid IDs (0 backfills needed)", entityName);
            }
            return totalUpdated;
        } catch (Exception e) {
            log.error("PublicIdBackfillRunner [{}]: error during backfill at lastId {}: {}",
                    entityName, lastId, e.getMessage(), e);
            throw e;
        }
    }
}

