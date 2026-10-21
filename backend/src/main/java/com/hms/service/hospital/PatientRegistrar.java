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
public class PatientRegistrar {

    @Autowired
    private PatientRepository patientRepository;

    /**
     * Insert a new patient and give it its collision-safe registration number, atomically.
     *
     * @param patient a new, unsaved patient with its tenant already set
     * @return the managed instance, carrying its generated id and customId
     */
    @Transactional
    public Patient persistNewPatient(Patient patient) {
        // saveAndFlush, not save: the INSERT has to reach the database before getId() can answer.
        Patient saved = patientRepository.saveAndFlush(patient);
        Long hospitalId = saved.getHospitalId();
        long candidateNumber = (saved.getId() != null && saved.getId() > 0) ? saved.getId() : 1;
        String candidate = "PAT" + candidateNumber;
        while (patientRepository.existsByHospitalIdAndCustomId(hospitalId, candidate)) {
            candidateNumber++;
            candidate = "PAT" + candidateNumber;
        }
        saved.setCustomId(candidate);
        // Dirty-checked; the UPDATE is written when this transaction flushes before commit.
        return saved;
    }
}
