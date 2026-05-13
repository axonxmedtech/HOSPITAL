package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.MedicineMasterRequest;
import com.hms.entity.pharmacy.MedicineMaster;
import com.hms.repository.pharmacy.MedicineMasterRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicineMasterService {

    @Autowired
    private MedicineMasterRepository repository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Transactional
    public MedicineMaster create(MedicineMasterRequest req) {
        MedicineMaster m = new MedicineMaster();
        m.setHospitalId(securityHelper.getCurrentHospitalId());
        mapDtoToEntity(req, m);
        return repository.save(m);
    }

    @Transactional
    public MedicineMaster update(Long id, MedicineMasterRequest req) {
        MedicineMaster m = repository.findByIdAndHospitalId(id, securityHelper.getCurrentHospitalId())
                .orElseThrow(() -> new RuntimeException("Medicine not found in catalog"));
        mapDtoToEntity(req, m);
        return repository.save(m);
    }

    public MedicineMaster getById(Long id) {
        return repository.findByIdAndHospitalId(id, securityHelper.getCurrentHospitalId())
                .orElseThrow(() -> new RuntimeException("Medicine not found"));
    }

    public Page<MedicineMaster> searchAndList(String query, Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        if (query != null && !query.trim().isEmpty()) {
            return repository.searchMedicines(hid, query, pageable);
        }
        return repository.findByHospitalId(hid, pageable);
    }

    public List<MedicineMaster> autocomplete(String query) {
        return repository.findTop10ByHospitalIdAndMedicineNameContainingIgnoreCase(securityHelper.getCurrentHospitalId(), query);
    }

    private void mapDtoToEntity(MedicineMasterRequest req, MedicineMaster m) {
        m.setMedicineCode(req.getMedicineCode());
        m.setMedicineName(req.getMedicineName());
        m.setGenericName(req.getGenericName());
        m.setCategoryId(req.getCategoryId());
        m.setManufacturerId(req.getManufacturerId());
        m.setMedicineType(req.getMedicineType());
        m.setScheduleType(req.getScheduleType());
        m.setDosageForm(req.getDosageForm());
        m.setStrength(req.getStrength());
        m.setUnitOfMeasure(req.getUnitOfMeasure());
        m.setReorderLevel(req.getReorderLevel() != null ? req.getReorderLevel() : 0);
        m.setGstPercentage(req.getGstPercentage());
        if (req.getRequiresPrescription() != null) m.setRequiresPrescription(req.getRequiresPrescription());
        if (req.getIsActive() != null) m.setIsActive(req.getIsActive());
    }
}
