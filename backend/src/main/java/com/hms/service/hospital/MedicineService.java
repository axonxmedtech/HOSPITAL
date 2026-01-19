package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.repository.MedicineRepository;
import com.hms.security.SecurityContextHelper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MedicineService {

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<Medicine> searchMedicines(String query) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return medicineRepository.searchByName("%" + query + "%", hospitalId);
    }

    public Medicine addMedicine(Medicine medicine) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Prevent duplicates
        if (medicineRepository.existsByNameAndHospitalId(medicine.getName(), hospitalId)) {
            throw new RuntimeException("Medicine already exists");
        }

        medicine.setHospitalId(hospitalId);
        return medicineRepository.save(medicine);
    }

    @PostConstruct
    public void seedMedicines() {
        if (medicineRepository.count() == 0) {
            System.out.println("Seeding initial medicines...");
            List<Medicine> initialMedicines = Arrays.asList(
                    createMedicine("Paracetamol", "Tablet", "500mg", "1-0-1", "3 Days", "Generic"),
                    createMedicine("Amoxicillin", "Capsule", "500mg", "1-1-1", "5 Days", "Generic"),
                    createMedicine("Ibuprofen", "Tablet", "400mg", "1-0-1", "3 Days", "Generic"),
                    createMedicine("Cetirizine", "Tablet", "10mg", "0-0-1", "3 Days", "Generic"),
                    createMedicine("Cough Syrup", "Syrup", "10ml", "1-1-1", "5 Days", "Generic"),
                    createMedicine("Azithromycin", "Tablet", "500mg", "1-0-0", "3 Days", "Generic"),
                    createMedicine("Metformin", "Tablet", "500mg", "1-0-1", "30 Days", "Generic"),
                    createMedicine("Amlodipine", "Tablet", "5mg", "1-0-0", "30 Days", "Generic"),
                    createMedicine("Omeprazole", "Capsule", "20mg", "1-0-0", "7 Days", "Generic"),
                    createMedicine("Pantoprazole", "Tablet", "40mg", "1-0-0", "7 Days", "Generic"));
            medicineRepository.saveAll(initialMedicines);
        }
    }

    private Medicine createMedicine(String name, String type, String dosage, String freq, String duration,
            String manufacturer) {
        Medicine m = new Medicine();
        m.setName(name);
        m.setType(type);
        m.setDefaultDosage(dosage);
        m.setDefaultFrequency(freq);
        m.setDefaultDuration(duration);
        m.setManufacturer(manufacturer);
        m.setIsActive(true);
        // Default Inventory Values for Seeding
        m.setStockQuantity(100);
        m.setUnitPrice(5.0); // Dummy price
        m.setMinStockLevel(10);
        m.setExpiryDate(java.time.LocalDate.now().plusYears(1));
        return m;
    }
}
