
package com.hms.component;

import com.hms.service.platform.PlatformHospitalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlatformStatsVerifier implements CommandLineRunner {

    @Autowired
    private PlatformHospitalService hospitalService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== HOSPITAL STATS VERIFICATION ===");
        try {
            Map<String, Long> stats = hospitalService.getHospitalStats();
            System.out.println("RAW STATS FROM SERVICE:");
            System.out.println("Total: " + stats.get("total"));
            System.out.println("Active: " + stats.get("active"));
            System.out.println("Inactive: " + stats.get("inactive"));

            System.out.println("\nChecking individual hospitals:");
            hospitalService.getAllHospitals(org.springframework.data.domain.Pageable.unpaged(), null)
                    .forEach(h -> System.out.println("Hospital: " + h.getName() + " | isActive: " + h.getIsActive()));

        } catch (Exception e) {
            System.out.println("Error verifying stats: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("===================================");
    }
}
