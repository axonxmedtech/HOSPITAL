package com.hms.component;

import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== DATA DIAGNOSTICS ===");

        List<Hospital> hospitals = hospitalRepository.findAll();
        System.out.println("Total Hospitals: " + hospitals.size());

        for (Hospital h : hospitals) {
            long patientCount = patientRepository.countByHospitalId(h.getId());
            System.out.println("Hospital: " + h.getName() + " (ID: " + h.getId() + ") - Patients: " + patientCount);
        }
        System.out.println("========================");

        // Ensure we have an admin for Hospital 1 (City General Hospital) which has data
        Optional<Hospital> cityHospital = hospitalRepository.findById(1L);
        if (cityHospital.isPresent()) {
            String auditEmail = "audit@cityhospital.com";
            if (!userRepository.findByEmail(auditEmail).isPresent()) {
                User auditUser = new User();
                auditUser.setName("Audit Admin");
                auditUser.setEmail(auditEmail);
                auditUser.setPassword(passwordEncoder.encode("password")); // known password
                auditUser.setRole("HOSPITAL_ADMIN");
                auditUser.setHospitalId(1L);
                auditUser.setIsActive(true);
                userRepository.save(auditUser);
                System.out.println("Created audit user: " + auditEmail);
            } else {
                System.out.println("Audit user " + auditEmail + " already exists.");
            }
        }
    }
}
