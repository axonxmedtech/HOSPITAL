package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class MedicineBatchService {

    @Autowired
    private MedicineBatchRepository repository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public Page<MedicineBatch> getInventory(String query, Long categoryId, Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        
        if (categoryId != null) {
            if (query != null && !query.trim().isEmpty()) {
                return repository.searchInventoryWithCategory(hid, query, categoryId, pageable);
            }
            return repository.findByHospitalIdAndMedicine_CategoryId(hid, categoryId, pageable);
        }

        if (query != null && !query.trim().isEmpty()) {
            return repository.searchInventory(hid, query, pageable);
        }
        return repository.findByHospitalId(hid, pageable);
    }

    public Page<MedicineBatch> getLowStockInventory(Pageable pageable) {
        return repository.findLowStock(securityHelper.getCurrentHospitalId(), pageable);
    }

    public Page<MedicineBatch> getExpiringInventory(Integer daysThreshold, Pageable pageable) {
        LocalDate dateLimit = LocalDate.now().plusDays(daysThreshold != null ? daysThreshold : 30);
        return repository.findExpiringSoon(securityHelper.getCurrentHospitalId(), dateLimit, pageable);
    }
}
