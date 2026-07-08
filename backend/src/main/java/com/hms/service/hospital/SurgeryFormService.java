package com.hms.service.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.SaveSurgeryFormRequest;
import com.hms.dto.SurgeryFormView;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Surgery;
import com.hms.entity.SurgeryForm;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.SurgeryFormRepository;
import com.hms.repository.SurgeryRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SurgeryFormService - save/read the OT/NABH surgery forms (generic JSON store).
 * HOSPITAL tenant only, OT-gated at the controller. Writes are nurse-only and
 * assignment-gated; reads also open to doctors/admins. One saved instance per
 * (admission, formType) — saving upserts.
 */
@Service
public class SurgeryFormService {

    private static final Logger logger = LoggerFactory.getLogger(SurgeryFormService.class);

    @Autowired private SurgeryFormRepository formRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private NurseAccessGuard nurseAccessGuard;
    @Autowired private com.hms.security.NurseWriteAccess nurseWriteAccess;
    @Autowired private com.hms.security.PerformingNurseResolver performingNurseResolver;
    @Autowired private ObjectMapper objectMapper;

    @Transactional
    public SurgeryFormView save(SaveSurgeryFormRequest req) {
        Long hospitalId = requireHospitalId();
        if (req.getIpdAdmissionId() == null) throw new IllegalArgumentException("ipdAdmissionId is required");
        if (req.getFormType() == null || req.getFormType().trim().isEmpty()) {
            throw new IllegalArgumentException("formType is required");
        }
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);
        nurseWriteAccess.assertCanWriteFor(admission.getId());

        String formType = req.getFormType().trim();
        SurgeryForm form = formRepository.findByIpdAdmissionIdAndFormType(admission.getId(), formType)
                .orElseGet(SurgeryForm::new);
        form.setHospitalId(hospitalId);
        form.setIpdAdmissionId(admission.getId());
        form.setFormType(formType);
        form.setSavedByUserId(securityHelper.getCurrentUserId());
        // Link the active surgery for reference, if any.
        surgeryRepository.findByIpdAdmissionIdAndStatusIn(admission.getId(),
                        List.of(Surgery.REQUESTED, Surgery.SCHEDULED, Surgery.IN_PROGRESS))
                .stream().findFirst().ifPresent(s -> form.setSurgeryId(s.getId()));
        form.setDataJson(writeJson(req.getData()));
        form.setPerformedByNurseId(performingNurseResolver.resolve(req.getPerformedByNurseId()));

        SurgeryForm saved = formRepository.save(form);
        return toView(saved);
    }

    /** Saved form for an admission + type, or null if not saved yet. */
    public SurgeryFormView get(Long ipdAdmissionId, String formType) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        if ("NURSE".equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }
        return formRepository.findByIpdAdmissionIdAndFormType(admission.getId(), formType)
                .map(this::toView).orElse(null);
    }

    /** Which form types are saved for this admission (for "Saved" badges). */
    public List<String> listSavedTypes(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        if ("NURSE".equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }
        return formRepository.findByIpdAdmissionId(admission.getId()).stream()
                .map(SurgeryForm::getFormType).collect(Collectors.toList());
    }

    // --- helpers ---

    private SurgeryFormView toView(SurgeryForm f) {
        SurgeryFormView v = new SurgeryFormView();
        v.setFormType(f.getFormType());
        v.setData(readJson(f.getDataJson()));
        v.setSavedAt(f.getUpdatedAt() != null ? f.getUpdatedAt() : f.getCreatedAt());
        return v;
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Collections.emptyMap() : data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid form data");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            logger.warn("Failed to parse surgery form JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private IpdAdmission requireAdmission(Long ipdAdmissionId, Long hospitalId) {
        IpdAdmission a = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        if (!hospitalId.equals(a.getHospitalId())) {
            throw new UnauthorizedException("Access denied: admission belongs to another hospital");
        }
        return a;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }
}
