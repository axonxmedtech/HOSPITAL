package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.repository.MedicineRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class InventoryService {

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<Medicine> getInventory() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return medicineRepository.findByHospitalId(hospitalId);
    }

    public List<Medicine> getLowStockMedicines() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return medicineRepository.findLowStock(hospitalId);
    }

    @Transactional
    public Medicine updateStock(Long medicineId, Integer quantityAdded) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found"));

        // Ensure hospital isolation
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (!medicine.getHospitalId().equals(hospitalId) && medicine.getHospitalId() != null) {
            // Note: Global medicines (hospitalId=null) might be read-only for stock?
            // Actually, for V1, we assume all inventory is hospital-specific.
            // If a global master list exists, we should probably 'clone' it to hospital
            // inventory on first use,
            // OR allow hospital to track stock for it.
            // For now, simple check.
            throw new UnauthorizedException("Unauthorized access to medicine");
        }

        medicine.setStockQuantity(medicine.getStockQuantity() + quantityAdded);
        return medicineRepository.save(medicine);
    }

    @Transactional
    public void deductStock(Long medicineId, Integer quantityDeducted) {
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found"));

        if (medicine.getStockQuantity() < quantityDeducted) {
            throw new IllegalArgumentException("Insufficient stock for: " + medicine.getName());
        }

        medicine.setStockQuantity(medicine.getStockQuantity() - quantityDeducted);
        medicineRepository.save(medicine);
    }

    // Helper for Pharmacy Controller dispensing
    @Transactional
    public void dispenseMedicine(String medicineName, Integer quantity) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        // Find medicine by name in this hospital
        // Optimistic: Assuming unique names or taking first match
        List<Medicine> meds = medicineRepository.searchByName(medicineName, hospitalId);
        if (meds.isEmpty()) {
            throw new ResourceNotFoundException("Medicine not found in inventory: " + medicineName);
        }
        Medicine med = meds.get(0);

        deductStock(med.getId(), quantity);
    }
}

