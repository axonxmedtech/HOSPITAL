package com.hms.service.hospital;

import com.hms.entity.AllergyMaster;
import com.hms.entity.PatientAllergy;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.AllergyMasterRepository;
import com.hms.repository.PatientAllergyRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientAllergyService {

    @Autowired private PatientAllergyRepository patientAllergyRepo;
    @Autowired private AllergyMasterRepository allergyMasterRepo;
    @Autowired private SecurityContextHelper securityHelper;

    public List<PatientAllergy> getPatientAllergies(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return patientAllergyRepo.findByPatientIdAndHospitalId(patientId, hospitalId);
    }

    @Transactional
    public PatientAllergy addAllergy(Long patientId, Long allergyMasterId, String severity, String notes) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        AllergyMaster master = allergyMasterRepo.findById(allergyMasterId)
            .filter(a -> a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Allergy master not found"));

        if (patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)) {
            throw new IllegalStateException("Allergy already recorded for this patient");
        }

        PatientAllergy pa = new PatientAllergy();
        pa.setHospitalId(hospitalId);
        pa.setPatientId(patientId);
        pa.setAllergyMasterId(allergyMasterId);
        pa.setSeverity(severity != null ? severity : "UNKNOWN");
        pa.setNotes(notes);
        pa.setRecordedByUserId(userId);
        return patientAllergyRepo.save(pa);
    }

    @Transactional
    public void removeAllergy(Long patientId, Long allergyId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PatientAllergy pa = patientAllergyRepo.findById(allergyId)
            .filter(a -> a.getPatientId().equals(patientId) && a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Patient allergy not found"));
        patientAllergyRepo.delete(pa);
    }
}
