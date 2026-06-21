package com.hms.component;

import com.hms.entity.User;
import com.hms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            if (!userRepository.existsByEmail("admin123@gmail.com")) {
                User superAdmin = new User();
                superAdmin.setEmail("admin123@gmail.com");
                superAdmin.setPassword(passwordEncoder.encode("pass123"));
                superAdmin.setName("Super Admin");
                superAdmin.setRole("SUPER_ADMIN");
                superAdmin.setHospitalId(null);
                superAdmin.setIsActive(true);
                userRepository.save(superAdmin);
                System.out.println("=================================================");
                System.out.println("[DataInitializer] Super Admin CREATED");
                System.out.println("[DataInitializer]   Email   : admin123@gmail.com");
                System.out.println("[DataInitializer]   Password: pass123");
                System.out.println("=================================================");
            } else {
                System.out.println("[DataInitializer] Super Admin already exists — skipping seed");
            }
        } catch (Exception e) {
            System.err.println("[DataInitializer] ERROR seeding Super Admin: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
