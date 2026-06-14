package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.SupplierRequest;
import com.hms.entity.pharmacy.Supplier;
import com.hms.repository.pharmacy.SupplierRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;

@Service
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Transactional
    public Supplier createSupplier(SupplierRequest req) {
        Supplier s = new Supplier();
        s.setHospitalId(securityHelper.getCurrentHospitalId());
        s.setSupplierName(req.getSupplierName());
        s.setContactPerson(req.getContactPerson());
        s.setPhone(req.getPhone());
        s.setEmail(req.getEmail());
        s.setAddress(req.getAddress());
        s.setGstNumber(req.getGstNumber());
        s.setDrugLicenseNumber(req.getDrugLicenseNumber());
        s.setCreditDays(req.getCreditDays() != null ? req.getCreditDays() : 0);
        s.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        return supplierRepository.save(s);
    }

    public Page<Supplier> getAll(String search, Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        if (hid == null) {
            throw new UnauthorizedException("Unauthenticated request - hospital ID missing");
        }
        if (search != null && !search.trim().isEmpty()) {
            return supplierRepository.findByHospitalIdAndSupplierNameContainingIgnoreCase(hid, search, pageable);
        }
        return supplierRepository.findByHospitalId(hid, pageable);
    }

    public Supplier getById(Long id) {
        return supplierRepository.findByIdAndHospitalId(id, securityHelper.getCurrentHospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
    }

    @Transactional
    public Supplier update(Long id, SupplierRequest req) {
        Supplier s = getById(id);
        s.setSupplierName(req.getSupplierName());
        s.setContactPerson(req.getContactPerson());
        s.setPhone(req.getPhone());
        s.setEmail(req.getEmail());
        s.setAddress(req.getAddress());
        s.setGstNumber(req.getGstNumber());
        s.setDrugLicenseNumber(req.getDrugLicenseNumber());
        s.setCreditDays(req.getCreditDays());
        if (req.getIsActive() != null)
            s.setIsActive(req.getIsActive());
        return supplierRepository.save(s);
    }
}
