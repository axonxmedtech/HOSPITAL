package com.hms.service.hospital;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one place a patient row comes into existence.
 *
 * <p>The registration number is derived from the auto-increment id and guaranteed unique
 * per hospital using the database uniqueness constraint and a bounded retry in a fresh transaction.
 */
@Component
public class PatientRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(PatientRegistrar.class);
    private static final int MAX_RETRIES = 10;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    /**
     * Insert a new patient and give it its collision-safe registration number, atomically.
     *
     * @param patient a new, unsaved patient with its tenant already set
     * @return the managed instance, carrying its generated id and customId
     */
    public Patient persistNewPatient(Patient patient) {
        if (transactionManager == null) {
            // Fallback for mocked unit test environments without transaction manager
            Patient saved = patientRepository.saveAndFlush(patient);
            Long hospitalId = saved.getHospitalId();
            long candidateNumber = (saved.getId() != null && saved.getId() > 0) ? saved.getId() : 1;
            String candidate = "PAT" + candidateNumber;
            while (patientRepository.existsByHospitalIdAndCustomId(hospitalId, candidate)) {
                candidateNumber++;
                candidate = "PAT" + candidateNumber;
            }
            saved.setCustomId(candidate);
            return saved;
        }

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Step 1: Insert patient row in a dedicated transaction so the auto-increment ID is assigned
        // and the row is committed.
        Patient saved = txTemplate.execute(status -> {
            patient.setCustomId(null);
            return patientRepository.saveAndFlush(patient);
        });

        if (saved == null || saved.getId() == null) {
            throw new IllegalStateException("Failed to persist patient entity");
        }

        Long hospitalId = saved.getHospitalId();
        final Long patientId = saved.getId();

        // Check if an explicit non-colliding custom ID was requested
        if (patient.getCustomId() != null && !patient.getCustomId().isBlank()) {
            final String requested = patient.getCustomId();
            if (!patientRepository.existsByHospitalIdAndCustomId(hospitalId, requested)) {
                try {
                    Boolean success = txTemplate.execute(status -> {
                        int updated = patientRepository.updateCustomId(hospitalId, patientId, requested);
                        return updated > 0;
                    });
                    if (Boolean.TRUE.equals(success)) {
                        saved.setCustomId(requested);
                        return saved;
                    }
                } catch (DataIntegrityViolationException e) {
                    logger.warn("Requested custom ID {} collided for patient {} (hospital {}), falling back to auto-allocation",
                            requested, patientId, hospitalId);
                }
            }
        }

        long candidateNumber = (saved.getId() > 0) ? saved.getId() : 1;
        String assignedCustomId = null;

        // Step 2: Bounded retry in a fresh transaction to assign unique customId safely
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String candidate = "PAT" + candidateNumber;
            while (patientRepository.existsByHospitalIdAndCustomId(hospitalId, candidate)) {
                candidateNumber++;
                candidate = "PAT" + candidateNumber;
            }

            final String candidateToTry = candidate;

            try {
                Boolean success = txTemplate.execute(status -> {
                    int updated = patientRepository.updateCustomId(hospitalId, patientId, candidateToTry);
                    return updated > 0;
                });

                if (Boolean.TRUE.equals(success)) {
                    assignedCustomId = candidateToTry;
                    break;
                }
            } catch (DataIntegrityViolationException e) {
                logger.warn("Custom ID collision on {} for patient {} (hospital {}), retrying (attempt {}/{})",
                        candidateToTry, patientId, hospitalId, attempt, MAX_RETRIES);
                candidateNumber++;
            }
        }

        if (assignedCustomId == null) {
            throw new IllegalStateException("Failed to assign unique patient custom ID after " + MAX_RETRIES + " attempts");
        }

        saved.setCustomId(assignedCustomId);
        return saved;
    }
}
