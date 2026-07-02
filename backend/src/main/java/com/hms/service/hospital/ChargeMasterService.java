package com.hms.service.hospital;

import com.hms.entity.ChargeMaster;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.ChargeMasterRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChargeMasterService {

    @Autowired
    private ChargeMasterRepository chargeMasterRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Transactional(readOnly = true)
    public List<ChargeMaster> getAll(Long hospitalId) {
        return chargeMasterRepository.findByHospitalId(hospitalId);
    }

    @Transactional(readOnly = true)
    public ChargeMaster getById(Long hospitalId, Long id) {
        return chargeMasterRepository.findByHospitalIdAndId(hospitalId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge Master entry not found"));
    }

    @Transactional
    public ChargeMaster create(Long hospitalId, ChargeMaster entry) {
        // Validate uniqueness of serviceCode per hospital
        if (chargeMasterRepository.findByHospitalIdAndServiceCode(hospitalId, entry.getServiceCode()).isPresent()) {
            throw new IllegalArgumentException("Service code already exists for this hospital");
        }
        entry.setHospitalId(hospitalId);
        return chargeMasterRepository.save(entry);
    }

    @Transactional
    public ChargeMaster update(Long hospitalId, Long id, ChargeMaster request) {
        ChargeMaster existing = getById(hospitalId, id);

        // Check if service code is changing and conflicts
        if (!existing.getServiceCode().equals(request.getServiceCode())) {
            if (chargeMasterRepository.findByHospitalIdAndServiceCode(hospitalId, request.getServiceCode()).isPresent()) {
                throw new IllegalArgumentException("Service code already exists for this hospital");
            }
            existing.setServiceCode(request.getServiceCode());
        }

        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setActivePrice(request.getActivePrice());
        existing.setEffectiveFrom(request.getEffectiveFrom());
        existing.setIsActive(request.getIsActive());

        return chargeMasterRepository.save(existing);
    }

    @Transactional
    public void delete(Long hospitalId, Long id) {
        ChargeMaster existing = getById(hospitalId, id);
        chargeMasterRepository.delete(existing);
    }
}
