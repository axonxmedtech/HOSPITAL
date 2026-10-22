package com.hms.service.hospital;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes tenant number allocation until the caller commits, across JVMs. */
@Component
public class TenantCustomIdAllocator {
    public static final int MAX_PROBES = 1000;
    @PersistenceContext private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTenant(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) throw new IllegalArgumentException("Hospital is required");
        if (entityManager.createNativeQuery("SELECT id FROM hospitals WHERE id = :tenant FOR UPDATE")
                .setParameter("tenant", hospitalId).getResultList().size() != 1) {
            throw new IllegalStateException("Hospital is missing during number allocation");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(String entity, Long hospitalId, Long start) {
        lockTenant(hospitalId);
        String table;
        String prefix;
        if ("Patient".equals(entity)) { table = "patients"; prefix = "PAT"; }
        else if ("Billing".equals(entity)) { table = "billing"; prefix = "BIL"; }
        else throw new IllegalArgumentException("Unsupported number type");
        if (start == null || start <= 0) throw new IllegalArgumentException("Positive allocation seed required");
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            String candidate = prefix + Math.addExact(start, probe);
            // A locking read sees commits made while waiting for the tenant lock even under
            // MySQL REPEATABLE READ. An ordinary COUNT could use an older transaction snapshot.
            if (entityManager.createNativeQuery("SELECT id FROM " + table
                    + " WHERE hospital_id = :tenant AND custom_id = :candidate FOR UPDATE")
                    .setParameter("tenant", hospitalId).setParameter("candidate", candidate)
                    .getResultList().isEmpty()) return candidate;
        }
        throw new IllegalStateException("Custom ID probe limit exhausted");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocateBilling(Long hospitalId) {
        lockTenant(hospitalId);
        var ids = entityManager.createNativeQuery(
                "SELECT id FROM billing WHERE hospital_id = :tenant ORDER BY id DESC LIMIT 1 FOR UPDATE")
                .setParameter("tenant", hospitalId).getResultList();
        long start = ids.isEmpty() ? 1001L : Math.max(1001L, Math.addExact(((Number) ids.get(0)).longValue(), 1000L));
        return allocate("Billing", hospitalId, start);
    }
}
