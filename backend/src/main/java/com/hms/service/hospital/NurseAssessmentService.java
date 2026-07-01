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

        // Structured BP: accept explicit systolic/diastolic, else parse the legacy string.
        Integer systolic = toInt(data.get("bpSystolic"));
        Integer diastolic = toInt(data.get("bpDiastolic"));
        String legacyBp = (String) data.get("bloodPressure");
        if ((systolic == null || diastolic == null) && legacyBp != null) {
            int[] parsed = parseBloodPressure(legacyBp);
            if (systolic == null) systolic = parsed[0] == -1 ? null : parsed[0];
            if (diastolic == null) diastolic = parsed[1] == -1 ? null : parsed[1];
        }
        v.setBpSystolic(systolic);
        v.setBpDiastolic(diastolic);
        // Keep the legacy string populated (normalize from structured when only structured was sent).
        if (legacyBp != null && !legacyBp.isBlank()) {
            v.setBloodPressure(legacyBp);
        } else if (systolic != null && diastolic != null) {
            v.setBloodPressure(systolic + "/" + diastolic);
        } else if (systolic != null) {
            v.setBloodPressure(String.valueOf(systolic));
        }

        v.setPulse(toInt(data.get("pulse")));
        v.setTemperature(toBigDecimal(data.get("temperature")));
        v.setSpo2(toInt(data.get("spo2")));
        v.setRespiratoryRate(toInt(data.get("respiratoryRate")));
        v.setPainScore(toInt(data.get("painScore")));
        v.setWeight(toBigDecimal(data.get("weight")));
        v.setOxygenSupport((String) data.get("oxygenSupport"));
        v.setRemarks((String) data.get("remarks"));
        v.setTempMethod((String) data.get("tempMethod"));
        v.setPulseRhythm((String) data.get("pulseRhythm"));
        v.setRespPattern((String) data.get("respPattern"));
        v.setBpPosition((String) data.get("bpPosition"));
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
        String s = val.toString().trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        String s = val.toString().trim();
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    /** Parses "120/80" (or "120 80") into [systolic, diastolic]; -1 for a token that is missing or non-numeric. */
    private int[] parseBloodPressure(String bp) {
        int[] out = { -1, -1 };
        if (bp == null || bp.isBlank()) return out;
        String[] parts = bp.trim().split("[/\\s]+");
        try { if (parts.length >= 1) out[0] = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        try { if (parts.length >= 2) out[1] = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
        return out;
    }
}
