package com.hms.service.hospital;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Patient (PAT…) and bill (BIL…) numbers, counted separately for each hospital.
 *
 * <p>Each hospital has its own counter per kind in {@code tenant_number_sequences}, so hospital A's
 * registrations never move hospital B's numbers. A hospital with no counter yet (new, or created
 * after V51) starts right after its own highest existing number, or at 1 when it has none. Numbers
 * already issued are never changed. Allocation is serialized per hospital by locking the hospital
 * row until the caller commits, across JVMs.
 */
@Component
public class TenantCustomIdAllocator {
    public static final int MAX_PROBES = 1000;
    static final String SEQUENCES_TABLE = "tenant_number_sequences";
    @PersistenceContext private EntityManager entityManager;

    /** Table, prefix and counter kind of one numbered entity. */
    private record Kind(String table, String prefix, String counter) {}

    private static Kind kind(String entity) {
        if ("Patient".equals(entity)) return new Kind("patients", "PAT", "PATIENT");
        if ("Billing".equals(entity)) return new Kind("billing", "BIL", "BILLING");
        throw new IllegalArgumentException("Unsupported number type");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTenant(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) throw new IllegalArgumentException("Hospital is required");
        if (entityManager.createNativeQuery("SELECT id FROM hospitals WHERE id = :tenant FOR UPDATE")
                .setParameter("tenant", hospitalId).getResultList().size() != 1) {
            throw new IllegalStateException("Hospital is missing during number allocation");
        }
    }

    /** The hospital's next patient number: PAT1, PAT2, … in that hospital's own series. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocatePatient(Long hospitalId) {
        return allocateNext("Patient", hospitalId);
    }

    /** The hospital's next bill number: BIL1, BIL2, … in that hospital's own series. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocateBilling(Long hospitalId) {
        return allocateNext("Billing", hospitalId);
    }

    private String allocateNext(String entity, Long hospitalId) {
        lockTenant(hospitalId);
        Kind k = kind(entity);
        List<?> row = entityManager.createNativeQuery("SELECT next_value FROM " + SEQUENCES_TABLE
                        + " WHERE hospital_id = :tenant AND kind = :kind FOR UPDATE")
                .setParameter("tenant", hospitalId).setParameter("kind", k.counter()).getResultList();
        long next = row.isEmpty()
                ? Math.addExact(highestIssued(k, hospitalId), 1L)
                : ((Number) row.get(0)).longValue();
        // The probe still skips a number an import or an older scheme already gave out.
        String id = allocate(entity, hospitalId, next);
        long following = Math.addExact(Long.parseLong(id.substring(k.prefix().length())), 1L);
        String write = row.isEmpty()
                ? "INSERT INTO " + SEQUENCES_TABLE + " (hospital_id, kind, next_value, updated_at)"
                        + " VALUES (:tenant, :kind, :next, CURRENT_TIMESTAMP)"
                : "UPDATE " + SEQUENCES_TABLE + " SET next_value = :next, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE hospital_id = :tenant AND kind = :kind";
        entityManager.createNativeQuery(write).setParameter("tenant", hospitalId)
                .setParameter("kind", k.counter()).setParameter("next", following).executeUpdate();
        return id;
    }

    /**
     * The highest plain number (PAT12, not PAT0012 or an imported code) this hospital has issued,
     * 0 if none. Only read once per hospital, before its counter exists.
     */
    private long highestIssued(Kind k, Long hospitalId) {
        List<?> top = entityManager.createNativeQuery("SELECT custom_id FROM " + k.table()
                        + " WHERE hospital_id = :tenant AND custom_id REGEXP :shape"
                        + " ORDER BY CHAR_LENGTH(custom_id) DESC, custom_id DESC LIMIT 1 FOR UPDATE")
                .setParameter("tenant", hospitalId)
                .setParameter("shape", "^" + k.prefix() + "[1-9][0-9]{0,17}$")
                .getResultList();
        return top.isEmpty() ? 0L : Long.parseLong(((String) top.get(0)).substring(k.prefix().length()));
    }

    /** The first free number at or after {@code start} in this hospital (skips taken ones). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(String entity, Long hospitalId, Long start) {
        lockTenant(hospitalId);
        Kind k = kind(entity);
        if (start == null || start <= 0) throw new IllegalArgumentException("Positive allocation seed required");
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            String candidate = k.prefix() + Math.addExact(start, probe);
            // A locking read sees commits made while waiting for the tenant lock even under
            // MySQL REPEATABLE READ. An ordinary COUNT could use an older transaction snapshot.
            if (entityManager.createNativeQuery("SELECT id FROM " + k.table()
                    + " WHERE hospital_id = :tenant AND custom_id = :candidate FOR UPDATE")
                    .setParameter("tenant", hospitalId).setParameter("candidate", candidate)
                    .getResultList().isEmpty()) return candidate;
        }
        throw new IllegalStateException("Custom ID probe limit exhausted");
    }
}
