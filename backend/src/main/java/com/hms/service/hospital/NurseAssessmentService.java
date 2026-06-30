package com.hms.service.hospital;

import com.hms.entity.NurseAssessment;
import com.hms.entity.VitalSigns;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.NurseAssessmentRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NurseAssessmentService {

    @Autowired private NurseAssessmentRepository assessmentRepository;
    @Autowired private VitalSignsRepository vitalsRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private MrdService mrdService;

    @Transactional
    public NurseAssessment createAssessment(Long admissionId, Map<String, Object> data) {
        mrdService.validateAdmissionActive(admissionId);
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (assessmentRepository.existsByIpdAdmissionId(admissionId))
            throw new IllegalStateException("Assessment already exists for this admission");

        NurseAssessment a = new NurseAssessment();
        a.setIpdAdmissionId(admissionId);
        a.setHospitalId(hospitalId);
        a.setBloodPressure((String) data.get("bloodPressure"));
        a.setPulse(toInt(data.get("pulse")));
        a.setTemperature(toBigDecimal(data.get("temperature")));
        a.setSpo2(toInt(data.get("spo2")));
        a.setHeight(toBigDecimal(data.get("height")));
        a.setWeight(toBigDecimal(data.get("weight")));
        a.setPainScore(toInt(data.get("painScore")));
        a.setAllergies((String) data.get("allergies"));
        a.setFallRisk((String) data.get("fallRisk"));
        a.setGeneralCondition((String) data.get("generalCondition"));
        a.setChiefComplaintOnAdmission((String) data.get("chiefComplaintOnAdmission"));
        a.setAssessedByName(securityHelper.getCurrentUserEmail());
        a.setAssessedAt(LocalDateTime.now());
        return assessmentRepository.save(a);
    }

    public NurseAssessment getAssessment(Long admissionId) {
        return assessmentRepository.findByIpdAdmissionId(admissionId).orElse(null);
    }

    @Transactional
    public VitalSigns recordVitals(Long admissionId, Map<String, Object> data) {
        mrdService.validateAdmissionActive(admissionId);
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        VitalSigns v = new VitalSigns();
        v.setIpdAdmissionId(admissionId);
        v.setHospitalId(hospitalId);
        v.setBloodPressure((String) data.get("bloodPressure"));
        v.setPulse(toInt(data.get("pulse")));
        v.setTemperature(toBigDecimal(data.get("temperature")));
        v.setSpo2(toInt(data.get("spo2")));
        v.setRecordedByName(securityHelper.getCurrentUserEmail());
        v.setRecordedAt(LocalDateTime.now());
        return vitalsRepository.save(v);
    }

    public List<VitalSigns> getVitals(Long admissionId) {
        return vitalsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(admissionId);
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        return Integer.parseInt(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
