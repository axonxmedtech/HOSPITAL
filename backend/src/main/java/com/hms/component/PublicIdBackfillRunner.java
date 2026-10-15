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

/**
 * PublicIdBackfillRunner - Backfills missing public and custom IDs on startup in bounded batches.
 *
 * <p><b>Hardened Production Implementation:</b>
 * <ul>
 *   <li><b>Bounded Memory (No OOM):</b> Cursor-based batched queries ({@value #BATCH_SIZE} rows/chunk).
 *       Selects only primitive/scalar fields (id, publicId, customId), avoiding entity graph inflation
 *       and respecting the 512 MB production heap ceiling.</li>
 *   <li><b>Zero Concurrency Overwrites:</b> Eliminates {@code entityManager.merge(detachedEntity)}.
 *       Employs narrowly-scoped, conditional {@code UPDATE} statements directly targeting only
 *       {@code publicId} and {@code customId} columns. Concurrent edits to patient demographics,
 *       phone numbers, consultation fees, or notes are never overwritten.</li>
 *   <li><b>Targeted Filtering:</b> Queries ONLY records where {@code publicId} or {@code customId}
 *       is missing, empty, or whitespace. Once backfilled, startup executes one fast query per table
 *       returning 0 rows in milliseconds.</li>
 *   <li><b>Strict Idempotency & Concurrency Safety:</b> Conditional predicate in UPDATE ensures
 *       that concurrent identifier updates in flight are preserved without collision.</li>
 *   <li><b>Correct Blank ID Handling:</b> Null, empty string, whitespace, and literal string "null"
 *       identifiers are cleanly repaired across hospitals, users, doctors, patients, appointments,
 *       billings, and audit logs.</li>
 *   <li><b>Safe Transactions & Interrupted Batch Resumption:</b> Each batch commits in its own
 *       transaction boundary. Interrupted runs cleanly resume at the first incomplete record.</li>
 * </ul>
 */
@Component
public class PublicIdBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);

    public static final int BATCH_SIZE = 250;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }

    private int backfillHospitals() {
        return backfillEntityWithCustomId(
                "Hospital",
                "e.type",
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null'",
                (id, extra) -> {
                    HospitalType type = extra instanceof HospitalType ? (HospitalType) extra : HospitalType.HOSPITAL;
                    String prefix = switch (type != null ? type : HospitalType.HOSPITAL) {
                        case CLINIC   -> "CLN";
                        case PHARMACY -> "PHR";
                        default       -> "HSP";
                    };
                    return prefix + (1000 + RANDOM.nextInt(9000));
                }
        );
    }

    private int backfillUsers() {
        return backfillEntityWithCustomId(
                "User",
                "e.role",
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR ((e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null') AND e.role NOT IN ('RECEPTIONIST', 'NURSE'))",
                (id, extra) -> {
                    String role = extra != null ? extra.toString() : null;
                    if ("RECEPTIONIST".equals(role) || "NURSE".equals(role)) {
                        return null;
                    }
                    String prefix = "USR";
                    if ("HOSPITAL_ADMIN".equals(role)) {
                        prefix = "ADM";
                    } else if ("DOCTOR".equals(role)) {
                        prefix = "DOC";
                    } else if ("SUPER_ADMIN".equals(role)) {
                        prefix = "SUP";
                    }
                    return prefix + (1000 + RANDOM.nextInt(9000));
                }
        );
    }

    private int backfillDoctors() {
        return backfillEntityWithCustomId(
                "Doctor",
                null,
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null'",
                (id, extra) -> "DOC" + (id != null ? id : (1000 + RANDOM.nextInt(9000)))
        );
    }

    private int backfillPatients() {
        return backfillEntityWithCustomId(
                "Patient",
                null,
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null'",
                (id, extra) -> "PAT" + (id != null ? id : (1000 + RANDOM.nextInt(9000)))
        );
    }

    private int backfillAppointments() {
        return backfillEntityWithCustomId(
                "Appointment",
                null,
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null'",
                (id, extra) -> "APT" + (1000 + RANDOM.nextInt(9000))
        );
    }

    private int backfillBillings() {
        return backfillEntityWithCustomId(
                "Billing",
                null,
                "e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null' OR e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null'",
                (id, extra) -> "BIL" + (1000 + RANDOM.nextInt(9000))
        );
    }

    private int backfillAuditLogs() {
        long lastId = 0L;
        int totalUpdated = 0;
        int totalExamined = 0;
        long startTime = System.currentTimeMillis();

        try {
            while (true) {
                final long currentLastId = lastId;
                List<Object[]> batch = entityManager.createQuery(
                        "SELECT e.id, e.publicId FROM AuditLog e " +
                        "WHERE (e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null') " +
                        "AND e.id > :lastId ORDER BY e.id ASC",
                        Object[].class)
                        .setParameter("lastId", currentLastId)
                        .setMaxResults(BATCH_SIZE)
                        .getResultList();

                if (batch.isEmpty()) {
                    break;
                }

                totalExamined += batch.size();
                Long batchMaxId = (Long) batch.get(batch.size() - 1)[0];
                if (batchMaxId == null || batchMaxId <= currentLastId) {
                    log.warn("PublicIdBackfillRunner [AuditLog]: non-increasing ID {} detected, aborting loop to prevent hang",
                            batchMaxId);
                    break;
                }
                lastId = batchMaxId;

                Integer updatedInBatch = transactionTemplate.execute(status -> {
                    int count = 0;
                    for (Object[] row : batch) {
                        Long id = (Long) row[0];
                        String existingPublicId = (String) row[1];

                        if (isBlank(existingPublicId)) {
                            int r = entityManager.createQuery(
                                    "UPDATE AuditLog e SET e.publicId = :pubId " +
                                    "WHERE e.id = :id AND (e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null')")
                                    .setParameter("pubId", UUID.randomUUID().toString())
                                    .setParameter("id", id)
                                    .executeUpdate();
                            if (r > 0) {
                                count++;
                            }
                        }
                    }
                    entityManager.clear();
                    return count;
                });

                if (updatedInBatch != null) {
                    totalUpdated += updatedInBatch;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            if (totalUpdated > 0) {
                log.info("PublicIdBackfillRunner [AuditLog]: examined {}, updated {} record(s) in {} ms",
                        totalExamined, totalUpdated, duration);
            } else {
                log.debug("PublicIdBackfillRunner [AuditLog]: all records contain valid IDs (0 backfills needed)");
            }
            return totalUpdated;
        } catch (Exception e) {
            log.error("PublicIdBackfillRunner [AuditLog]: error during backfill at lastId {}: {}",
                    lastId, e.getMessage(), e);
            throw e;
        }
    }

    @FunctionalInterface
    private interface CustomIdGenerator {
        String generate(Long id, Object extra);
    }

    /**
     * Executes batched ID-windowed queries to find and backfill records with missing public/custom IDs.
     *
     * <p>Concurrency Safety Guarantee:
     * <ul>
     *   <li>Does NOT load or merge detached entity objects into the persistence context.</li>
     *   <li>Executes targeted, conditional {@code UPDATE} queries strictly on the primary key and missing ID columns.</li>
     *   <li>Concurrent changes to other columns (such as patient names, phone numbers, notes) remain untouched.</li>
     * </ul>
     */
    private int backfillEntityWithCustomId(
            String entityName,
            String extraSelect,
            String missingCondition,
            CustomIdGenerator customIdGenerator) {

        long lastId = 0L;
        int totalUpdated = 0;
        int totalExamined = 0;
        long startTime = System.currentTimeMillis();

        String selectFields = "e.id, e.publicId, e.customId" + (extraSelect != null ? ", " + extraSelect : "");

        try {
            while (true) {
                final long currentLastId = lastId;
                List<Object[]> batch = entityManager.createQuery(
                        "SELECT " + selectFields + " FROM " + entityName + " e " +
                        "WHERE (" + missingCondition + ") AND e.id > :lastId ORDER BY e.id ASC",
                        Object[].class)
                        .setParameter("lastId", currentLastId)
                        .setMaxResults(BATCH_SIZE)
                        .getResultList();

                if (batch.isEmpty()) {
                    break;
                }

                totalExamined += batch.size();
                Long batchMaxId = (Long) batch.get(batch.size() - 1)[0];
                if (batchMaxId == null || batchMaxId <= currentLastId) {
                    log.warn("PublicIdBackfillRunner [{}]: non-increasing ID {} detected, aborting loop to prevent hang",
                            entityName, batchMaxId);
                    break;
                }
                lastId = batchMaxId;

                Integer updatedInBatch = transactionTemplate.execute(status -> {
                    int count = 0;
                    for (Object[] row : batch) {
                        Long id = (Long) row[0];
                        String existingPublicId = (String) row[1];
                        String existingCustomId = (String) row[2];
                        Object extra = extraSelect != null && row.length > 3 ? row[3] : null;

                        boolean needPublicId = isBlank(existingPublicId);
                        boolean needCustomId = isBlank(existingCustomId);

                        boolean recordUpdated = false;

                        if (needPublicId) {
                            int r = entityManager.createQuery(
                                    "UPDATE " + entityName + " e SET e.publicId = :pubId " +
                                    "WHERE e.id = :id AND (e.publicId IS NULL OR TRIM(e.publicId) = '' OR e.publicId = 'null')")
                                    .setParameter("pubId", UUID.randomUUID().toString())
                                    .setParameter("id", id)
                                    .executeUpdate();
                            if (r > 0) {
                                recordUpdated = true;
                            }
                        }

                        if (needCustomId && customIdGenerator != null) {
                            String newCustomId = customIdGenerator.generate(id, extra);
                            if (newCustomId != null) {
                                int r = entityManager.createQuery(
                                        "UPDATE " + entityName + " e SET e.customId = :custId " +
                                        "WHERE e.id = :id AND (e.customId IS NULL OR TRIM(e.customId) = '' OR e.customId = 'null')")
                                        .setParameter("custId", newCustomId)
                                        .setParameter("id", id)
                                        .executeUpdate();
                                if (r > 0) {
                                    recordUpdated = true;
                                }
                            }
                        }

                        if (recordUpdated) {
                            count++;
                        }
                    }
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
