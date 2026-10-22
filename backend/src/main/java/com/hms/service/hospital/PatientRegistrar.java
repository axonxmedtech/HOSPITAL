package com.hms.service.hospital;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place a patient row comes into existence.
 *
 * <p>The registration number is derived from the auto-increment id and guaranteed unique
 * per hospital, avoiding collisions with pre-existing or manual custom IDs.
 */
@Component
@org.springframework.context.annotation.Import(TenantCustomIdAllocator.class)
public class PatientRegistrar {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired private TenantCustomIdAllocator customIdAllocator;

    /**
     * Insert a new patient and give it its collision-safe registration number, atomically.
     *
     * @param patient a new, unsaved patient with its tenant already set
     * @return the managed instance, carrying its generated id and customId
     */
    @Transactional
    public Patient persistNewPatient(Patient patient) {
        if (patient.getId() != null) throw new IllegalArgumentException("New patient must not have an ID");
        customIdAllocator.lockTenant(patient.getHospitalId());
        Patient saved = patientRepository.saveAndFlush(patient);
        if (saved.getCustomId() == null || saved.getCustomId().isBlank()) {
            saved.setCustomId(customIdAllocator.allocate("Patient", saved.getHospitalId(), saved.getId()));
        }
        // Existing supplied identifiers are preserved; the unique constraint rejects collisions.
        patientRepository.flush();
        return saved;
    }
}
